define(["esri/layers/KMLLayer", "dojo/Deferred", "dojo/request/xhr"],
function(KMLLayer, Deferred, xhr) {
  return {
    createStashedKmlLayer: function(kmlUrl, stashUrl) {
      var deferred = new Deferred();
      xhr(kmlUrl, {
        handleAs: "arraybuffer"
      }).then(function (data) {
        var kmlBlob = new Blob([data]);
        var formData = new FormData();
        var filename = kmlUrl.substring(kmlUrl.lastIndexOf('/') + 1);
        formData.append(filename, kmlBlob);
        xhr(stashUrl, {
          data: formData,
          handleAs: "json",
          method: "POST"
        }).then(function (data) {
          var kml = new KMLLayer(stashUrl + "/" + data[0]);
          deferred.resolve(kml);
        }, function (error) {
          console.log("Error stashing file: " + error);
        });
      }, function (error) {
        console.log("Error downloading file: " + error);
      }, function (evt) {
        console.log("Downloading file: " + evt);
      });
      return deferred;
    }
  };
});