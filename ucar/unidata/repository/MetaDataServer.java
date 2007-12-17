/**
 * $Id: TrackDataSource.java,v 1.90 2007/08/06 17:02:27 jeffmc Exp $
 *
 * Copyright 1997-2005 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package ucar.unidata.repository;


import ucar.unidata.data.SqlUtil;


import ucar.unidata.util.HtmlUtil;


import ucar.unidata.util.HttpServer;
import ucar.unidata.util.IOUtil;


import ucar.unidata.util.LogUtil;
import ucar.unidata.util.Misc;
import ucar.unidata.util.StringUtil;

import java.io.*;

import java.net.*;


import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;



/**
 *
 *
 * @author IDV Development Team
 * @version $Revision: 1.3 $
 */
public class MetaDataServer extends HttpServer implements Constants {

    /** _more_ */
    Repository repository;


    /**
     * _more_
     *
     * @param args _more_
     * @throws Throwable _more_
     */
    public MetaDataServer(String[] args) throws Throwable {
        super(8080);
        repository = new Repository(args);
        repository.init();
    }


    /**
     * _more_
     *
     * @param handler _more_
     * @param ok _more_
     * @param result _more_
     *
     * @throws Exception _more_
     */
    protected void writeContent(RequestHandler handler, boolean ok,
                                Result result)
            throws Exception {
        if (result.isHtml() && result.getShouldDecorate()) {
            String template = repository.getResource(PROP_HTML_TEMPLATE);
            String html = StringUtil.replace(template, "${content}",
                                             new String(result.getContent()));
            html = StringUtil.replace(html, "${title}", result.getTitle());
            html = StringUtil.replace(html, "${root}",
                                      repository.getUrlBase());
            List   links     = (List) result.getProperty(PROP_NAVLINKS);
            String linksHtml = "&nbsp;";
            if (links != null) {
                linksHtml = StringUtil.join("&nbsp;|&nbsp;", links);
            }

            List   sublinks     = (List) result.getProperty(PROP_NAVSUBLINKS);
            String sublinksHtml = "";
            if (sublinks != null) {
                sublinksHtml = StringUtil.join("\n&nbsp;|&nbsp;\n", sublinks);
            }


            html = StringUtil.replace(html, "${links}", linksHtml);
            if (sublinksHtml.length() > 0) {
                html = StringUtil.replace(html, "${sublinks}",
                                          "<div class=\"subnav\">"
                                          + sublinksHtml + "</div>");
            } else {
                html = StringUtil.replace(html, "${sublinks}", "");
            }
            handler.writeResult(ok, html, result.getMimeType());
        } else if (result.getInputStream() != null) {
            handler.writeResult(ok, result.getInputStream(),
                                result.getMimeType());

        } else {
            handler.writeResult(ok, result.getContent(),
                                result.getMimeType());
        }
    }




    /**
     * _more_
     *
     * @param socket _more_
     *
     * @return _more_
     *
     * @throws Exception _more_
     */
    protected RequestHandler doMakeRequestHandler(final Socket socket)
            throws Exception {
        return new RequestHandler(this, socket) {

            protected void writeHeaderArgs() throws Exception {
                writeLine("Cache-Control: no-cache" + CRLF);
                writeLine("Last-Modified:" + new Date() + CRLF);
            }

            protected void handleRequest(String path, Hashtable formArgs,
                                         Hashtable httpArgs, String content)
                    throws Exception {
                path = path.trim();
                //                formArgs = SqlUtil.cleanUpArguments(formArgs);
                try {
                    User user = repository.findUser("jdoe");
                    //user    = repository.findUser("anonymous");
                    RequestContext context = new RequestContext(user);
                    Request request = new Request(repository, path, context,
                                          formArgs);
                    if (user == null) {
                        Result result =
                            new Result("Error",
                                       new StringBuffer("Unknown request:"
                                           + path));
                        result.putProperty(PROP_NAVLINKS,
                                           repository.getNavLinks(request));
                        writeContent(this, false, result);
                        return;
                    }

                    Result result = repository.handleRequest(request);
                    if (result != null) {
                        writeContent(this, true, result);
                    } else {
                        //Try to serve up the file
                        String type = repository.getMimeTypeFromSuffix(
                                          IOUtil.getFileExtension(path));
                        path = StringUtil.replace(path,
                                repository.getUrlBase(), "");
                        if ((path.trim().length() == 0) || path.equals("/")) {
                            result = new Result("Error",
                                    new StringBuffer("Unknown request:"
                                        + path));
                            result.putProperty(PROP_NAVLINKS,
                                    repository.getNavLinks(request));
                            writeContent(this, false, result);
                            return;
                        }
                        try {
                            InputStream is =
                                IOUtil.getInputStream(
                                    "/ucar/unidata/repository/htdocs" + path,
                                    getClass());
                            writeResult(true, is, type);
                        } catch (IOException fnfe) {
                            result = new Result("Error",
                                    new StringBuffer("Unknown request:"
                                        + path));
                            result.putProperty(PROP_NAVLINKS,
                                    repository.getNavLinks(request));
                            writeContent(this, false, result);
                        }
                    }
                } catch (Throwable exc) {
                    exc = LogUtil.getInnerException(exc);
                    if (exc instanceof Request.BadInputException) {
                        Result result =
                            new Result("Error",
                                       new StringBuffer(exc.getMessage()));
                        result.putProperty(PROP_NAVLINKS,
                                           repository.getNavLinks(null));
                        writeContent(this, false, result);
                        exc.printStackTrace();
                    } else {
                        System.err.println("Error:" + exc);
                        exc.printStackTrace();
                        String trace = LogUtil.getStackTrace(exc);
                        writeContent(this, false,
                                     new Result("Error",
                                         new StringBuffer("<pre>" + trace
                                             + "</pre>")));
                    }
                }
            }
        };
    }



    /**
     * _more_
     *
     * @param msg _more_
     * @param exc _more_
     */
    protected void handleError(String msg, Exception exc) {
        System.err.println("Error:" + exc);
        exc.printStackTrace();
    }



    /**
     * _more_
     *
     * @param args _more_
     *
     * @throws Throwable _more_
     */
    public static void main(String[] args) throws Throwable {
        System.setProperty("derby.system.home", "foobar");
        MetaDataServer mds = new MetaDataServer(args);
        mds.init();
    }



}

