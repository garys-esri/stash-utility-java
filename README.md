# stash-utility-java

The Stash Utility stores, or "stashes," an uploaded file on a web server for subsequent retrieval through a URL with a unique ID.

## Why is the Stash Utility useful?

There may be various uses for the Stash Utility. The original intent was to enhance the capability of the [`KMLLayer` class](https://developers.arcgis.com/javascript/jsapi/kmllayer-amd.html) in the ArcGIS API for JavaScript. As of the 3.14 release of that API, the `KMLLayer` class adds KML or KMZ to a JavaScript map control by sending the KML URL to a sharing service on ArcGIS Server or ArcGIS Online, which translates the KML to JSON, which the `KMLLayer` class renders on the map. One challenge presented by this architecture is when a secured KML is used. The KML sharing service does not have the user's authentication credentials and cannot access a secured KML. Some sort of proxy may work for basic authentication, but a proxy will not work for client certificate authentication (i.e. PKI, e.g. CAC) because you should not and must not send your private key to anything, including a proxy. Therefore, we use the following workflow:

1. The browser uses an `XMLHttpRequest` to download the KML, using the user's credentials.
2. The browser uploads the KML to the Stash Utility in a multipart request.
3. The Stash Utility stores the KML and returns a unique ID to the browser.
4. The browser creates a new KML URL by taking the Stash Utility URL and appending the unique ID.
5. The browser creates a `KMLLayer` object using the new KML URL and initializes the layer by adding it to the map.
6. During initialization of the `KMLLayer`, the browser sends the new KML URL to the KML sharing service, which reads the new KML URL and translates it to JSON for the `KMLLayer` to display.

## How does the Stash Utility work?

The Stash Utility lets you **stash** a file and then **retrieve** that file later.

To **stash** one or more files, send a multipart request to the `StashServlet` URL, e.g. https://host.domain.com/stashutility/stash . For each part, set the name of the part to be the filename, and set the value of the part to be the file contents. In a client-side web application, you can do this in an HTML form, or you can do it in JavaScript. A JavaScript KML-specific example is found in [stashUtils.js](source/StashUtility/web/js/esridefensese/stash/stashUtils.js). The response is an array of unique IDs, like this:

    ["9830f25e-3c42-447d-a5a3-615c2bd2f73e", "a437d6dd-675d-4864-9963-a4f2b5d4499b"]

Each ID represents a file that can be retrieved. To **retrieve** a file, use the `StashServlet` URL and append a slash and the unique ID, e.g. https://host.domain.com/stashutility/stash/a437d6dd-675d-4864-9963-a4f2b5d4499b .

By default, stashed files are deleted after one retrieval. If configured not to delete after one retrieval, a cleanup thread will delete stashed files older than a certain age. You can change these behaviors by editing web.xml, either before building the web application or on the server where you deploy the Stash Utility (see below). 

## What should I consider when using the Stash Utility?

- The client browser must be able to retrieve the original file that is to be stashed, including authentication and authorization to that file.
- The request to **stash** and the response to **retrieve** contain files. The response to **stash** contains plain-text unique IDs, which can be used to retrieve files. If you don't want anyone else to be able to get your files, you should use HTTPS when calling both the **stash** and the **retrieve** operations.
- The client browser must be able to access the Stash Utility, including validation of the Stash Utility's HTTPS certificate.
- The process that retrieves from the stash must be able to access the Stash Utility, including validation of the Stash Utility's HTTPS certificate. In the case of the ArcGIS `KMLLayer`, the KML sharing service must be able to access the Stash Utility and validate its certificate. By default, the KML sharing service URL is //www.arcgis.com/sharing/kml . The KML sharing service on arcgis.com can only access URLs on the public Internet, so the Stash Utility must be accessible by public URL, and if using HTTPS, the Stash Utility's certificate must be issued by a well-known certificate authority (CA). The alternative is to use the KML sharing service on Portal for ArcGIS, an extension to ArcGIS Server. In that case, the ArcGIS Server machine must be able to access the Stash Utility and validate its certificate. If the certificate is not well known and you still want to use it, you can configure Portal for ArcGIS to accept certificates from other CAs, such as an internal corporate CA.

## How do I setup and configure the Stash Utility?

1. Set up a Java servlet container or application server. We tested it with Apache Tomcat 7.0.64 and JDK 1.8 64-bit on Windows 7 and on Red Hat Enterprise Linux 7. (On Linux, we used mod_jk to connect Tomcat to Apache HTTP Server to run on well-known ports.)
2. Build the web application in [source/StashUtility](source/StashUtility) and deploy it to your server.
3. If desired, edit WEB-INF/web.xml to change the `init-param` values. These parameters are documented in [StashServlet.java](source/StashUtility/src/java/com/esri/defense/se/stashutility/StashServlet.java). You can edit web.xml on your development machine before building the web application or on the web server after deploying the web application. (If you edit web.xml on the web server, restart the application or server after editing web.xml.)
4. See [example.html](source/StashUtility/web/example.html) to see how to use the Stash Utility with the ArcGIS `KMLLayer` class. You will need to edit that file to match your configuration.

## Issues

Find a bug or want to request a new feature?  Please let us know by [submitting an issue](issues/new).

## Contributing

Esri welcomes contributions from anyone and everyone. Please see our [guidelines for contributing](https://github.com/esri/contributing).

## Licensing

Copyright 2015 Esri

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

A copy of the license is available in the repository's
[license.txt](license.txt) file.

[](Esri Tags: ArcGIS Java JavaScript KML)
[](Esri Language: Java)
