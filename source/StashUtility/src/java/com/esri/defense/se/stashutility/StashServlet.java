/*******************************************************************************
 * Copyright 2015 Esri
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 ******************************************************************************/
package com.esri.defense.se.stashutility;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.activation.MimetypesFileTypeMap;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

/**
 * Stores a submitted file for later retrieval by a unique ID. You should serve
 * this servlet only over HTTPS to ensure the submitted files cannot be retrieved
 * by third parties.<br/>
 * <br/>
 * Initialization parameters:
 * <table>
 *      <tr>
 *          <th>Parameter name</th>
 *          <th>Description</th>
 *          <th>Default value</th>
 *      </tr>
 *      <tr>
 *          <td>cleanupIntervalMs</td>
 *          <td>The cleanup interval in milliseconds. The cleanup TimerTask will
 *              start running at an interval of this many milliseconds and continue
 *              to do so for the life of the servlet.</td>
 *          <td>DEFAULT_CLEANUP_INTERVAL_MS</td>
 *      </tr>
 *      <tr>
 *          <td>deleteAfterAccess</td>
 *          <td>If true, each stashed file will be deleted after it has been accessed
 *              once. If false, each stashed file will remain available until maxFileAgeMs
 *              milliseconds have passed.</td>
 *          <td>DEFAULT_DELETE_AFTER_ACCESS</td>
 *      </tr>
 *      <tr>
 *          <td>maxFileAgeMs</td>
 *          <td>The maximum file age in milliseconds. The cleanup TimerTask will
 *              delete files that are older than this many milliseconds.</td>
 *          <td>DEFAULT_MAX_FILE_AGE_MS</td>
 *      </tr>
 *      <tr>
 *          <td>stashDir</td>
 *          <td>The absolute path to the directory where all stashed files are stored.</td>
 *          <td>DEFAULT_STASH_DIR</td>
 *      </tr>
 * </table>
 * To stash a file, submit it as part of a multipart request, where the part's name
 * is the filename. The return value is ["unique-id"], where unique-id is used to
 * retrieve the file. If you submit multiple files in a single request, each one
 * is stored, and their unique IDs are returned in the same order as the submitted
 * files, e.g. ["unique-id-1", "unique-id-2", "unique-id-n"].<br/>
 * <br/>
 * To retrieve a file, call this servlet, appending "/unique-id" at the end of the
 * URL. For example, use https://hostname.domain.com/stashutility/stash/deadbeef-1234
 * for unique ID deadbeef-1234.
 */
@MultipartConfig
public class StashServlet extends HttpServlet {
    
    private static final Logger logger = Logger.getLogger(StashServlet.class.getName());
    
    private static final long DEFAULT_CLEANUP_INTERVAL_MS = 1000;
    private static final String DEFAULT_STASH_DIR = System.getProperty("java.io.tmpdir") + "/stashDir";
    private static final long DEFAULT_MAX_FILE_AGE_MS = 5000;
    private static final boolean DEFAULT_DELETE_AFTER_ACCESS = true;
    
    private File stashDir = null;
    private boolean deleteAfterAccess = true;
    
    private final TimerTask cleanupTask = new TimerTask() {

        @Override
        public void run() {
            logger.log(Level.FINE, "Cleanup task running");
            long maxFileAgeMs;
            try {
                maxFileAgeMs = Long.parseLong(getInitParameter("maxFileAgeMs"));
            } catch (Throwable t) {
                maxFileAgeMs = DEFAULT_MAX_FILE_AGE_MS;
            }
            logger.log(Level.FINE, "Max file age is {0} ms", maxFileAgeMs);
            if (!stashDir.exists()) {
                stashDir.mkdir();
            }
            File[] stashFiles = stashDir.listFiles();
            if (null != stashFiles) {
                for (File file : stashFiles) {
                    long lastModifiedPlusMaxAge = file.lastModified() + maxFileAgeMs;
                    if (lastModifiedPlusMaxAge < System.currentTimeMillis()) {
                        deleteRecursively(file);
                    }
                }
            }
        }
        
    };
    
    private final Timer cleanupTimer = new Timer(true);

    @Override
    public void init() throws ServletException {
        logger.log(Level.INFO, "StashServlet.init");
        
        String stashDirString = getInitParameter("stashDir");
        if (null == stashDirString) {
            stashDirString = DEFAULT_STASH_DIR;
        }
        stashDir = new File(stashDirString);
        logger.log(Level.INFO, "Stash directory is {0}", stashDir.getAbsolutePath());
        
        try {
            deleteAfterAccess = Boolean.parseBoolean(getInitParameter("deleteAfterAccess"));
        } catch (Throwable t) {
            deleteAfterAccess = DEFAULT_DELETE_AFTER_ACCESS;
        }
        logger.log(Level.INFO, "Delete after access? {0}", deleteAfterAccess);
        
        long cleanupIntervalMs;
        try {
            cleanupIntervalMs = Long.parseLong(getInitParameter("cleanupIntervalMs"));
        } catch (Throwable t) {
            cleanupIntervalMs = DEFAULT_CLEANUP_INTERVAL_MS;
        }
                
        cleanupTimer.schedule(cleanupTask, 0, cleanupIntervalMs);
        logger.log(Level.INFO, "Scheduled cleanupTask to run at interval {0} ms", cleanupIntervalMs);
    }

    @Override
    public void destroy() {
        cleanupTimer.cancel();
    }

    /**
     * Processes requests for both HTTP
     * <code>GET</code> and
     * <code>POST</code> methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String contentType = request.getContentType();
        if (null != contentType && (contentType.startsWith("multipart/form-data") || contentType.startsWith("multipart/mixed"))) {
            //This is an upload; write the content to the stash
            Collection<Part> parts = request.getParts();
            UUID[] uids = new UUID[parts.size()];
            Iterator<Part> partsIterator = parts.iterator();
            int counter = -1;
            while (partsIterator.hasNext()) {
                Part part = partsIterator.next();
                counter++;
                try {
                    UUID uid = UUID.randomUUID();
                    File dir = new File(stashDir, uid.toString());
                    dir.mkdir();
                    part.write(new File(dir, part.getName()).getAbsolutePath());
                    uids[counter] = uid;
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "Couldn't copy " + part.getName() + " to disk", t);
                }
            }

            response.setContentType("text/json;charset=UTF-8");
            PrintWriter out = response.getWriter();
            try {
                out.print("[");
                for (UUID uid : uids) {
                    out.print("\"" + (null == uid ? "" : uid.toString()) + "\"");
                }
                out.print("]");
            } finally {            
                out.close();
            }
        } else {
            //This is not an upload; retrieve a file from the stash
            String pathInfo = request.getPathInfo();
            if (null != pathInfo && 1 < pathInfo.length()) {
                String id = pathInfo.substring(1);
                final File dir = new File(stashDir, id);
                File[] listFiles = dir.listFiles();
                if (null != listFiles && 0 < listFiles.length) {
                    File file = listFiles[0];//There should only be one
                    MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
                    String outContentType = mimeTypesMap.getContentType(file);
                    response.setContentType(outContentType);
                    DataOutputStream dataOut = new DataOutputStream(response.getOutputStream());
                    byte[] bytes = new byte[8192];
                    final DataInputStream dataIn = new DataInputStream(new FileInputStream(file));
                    int bytesRead;
                    while (-1 != (bytesRead = dataIn.read(bytes))) {
                        dataOut.write(bytes, 0, bytesRead);
                    }
                    dataOut.close();
                    new Thread() {

                        @Override
                        public void run() {
                            try {
                                dataIn.close();
                            } catch (IOException ex) {
                                logger.log(Level.SEVERE, null, ex);
                            }
                            if (deleteAfterAccess) {
                                deleteRecursively(dir);
                            }
                        }
                        
                    }.start();
                }
            }
        }
    }
    
    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] subfiles = file.listFiles();
            for (File subfile : subfiles) {
                deleteRecursively(subfile);
            }
            file.delete();
        } else {
            file.delete();
        }
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP
     * <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP
     * <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>
}
