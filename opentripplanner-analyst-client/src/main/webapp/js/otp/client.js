/* This program is free software: you can redistribute it and/or
   modify it under the terms of the GNU Lesser General Public License
   as published by the Free Software Foundation, either version 3 of
   the License, or (at your option) any later version.
   
   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.
   
   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>. 
*/

var INIT_LOCATION = new L.LatLng(38.9538, -76.8851); // new carrolton
var AUTO_CENTER_MAP = true;
var ROUTER_ID = "";

var map = new L.Map('map', {
	minZoom : 10,
	maxZoom : 16,
	// what we really need is a fade transition between old and new tiles without removing the old ones
	//fadeAnimation: false
});

var mapboxURL = "http://{s}.tiles.mapbox.com/v3/mapbox.mapbox-streets/{z}/{x}/{y}.png";
var OSMURL    = "http://{s}.mqcdn.com/tiles/1.0.0/osm/{z}/{x}/{y}.png";
var aerialURL = "http://{s}.mqcdn.com/naip/{z}/{x}/{y}.png";

var mapboxAttrib = "Tiles from <a href='http://mapbox.com/about/maps' target='_blank'>MapBox Streets</a>";
var mapboxLayer = new L.TileLayer(mapboxURL, 
		{subdomains: ["a","b","c","d"], maxZoom: 16, attribution: mapboxAttrib});

var osmAttrib = 'Map data &copy; 2011 OpenStreetMap contributors';
var osmLayer = new L.TileLayer(OSMURL, 
		{subdomains: ["otile1","otile2","otile3","otile4"], maxZoom: 16, attribution: osmAttrib});

var aerialLayer = new L.TileLayer(aerialURL, 
		{subdomains: ["oatile1","oatile2","oatile3","oatile4"], maxZoom: 16, attribution: osmAttrib});

var flags = {
	twoEndpoint: false,
    startTime: 'none',
    endTime: 'none'
};

var params = {
    layers: 'traveltime',
    // this is always left at gray; color schemes are done client-side
    styles: 'gray',
    batch: true,
};

// convert a map of query parameters into a query string, 
// expanding Array values into multiple query parameters
var buildQuery = function(params) {
	ret = [];
	for (key in params) {
		vals = params[key];
		// wrap scalars in array
		if ( ! (vals instanceof Array)) vals = new Array(vals);
		for (i in vals) { 
			val = vals[i]; // js iterates over indices not values!
			// skip params that are empty or stated to be the same as previous
			if (val == '' || val == 'same')
				continue;
			param = [encodeURIComponent(key), encodeURIComponent(val)].join('=');
			ret.push(param);
		}
	}
	return "?" + ret.join('&');
};

// http://en.wikipedia.org/wiki/HSL_and_HSV#Converting_to_RGB
var HSVtoRGB = function (h, s, v) {
    var chroma = v*s;
    var hprime = h/60;
    var r_1, g_1, b_1;

    var x = chroma * (1 - Math.abs(hprime % 2 - 1));

    if (hprime < 1) {
        r_1 = chroma;
        g_1 = x;
        b_1 = 0;
    }
    else if (hprime < 2) {
        r_1 = x;
        g_1 = chroma;
        b_1 = 0;
    }
    else if (hprime < 3) {
        r_1 = 0;
        g_1 = chroma;
        b_1 = x;
    }
    else if (hprime < 4) {
        r_1 = 0;
        g_1 = x;
        b_1 = chroma;
    }
    else if (hprime < 5) {
        r_1 = x;
        g_1 = 0;
        b_1 = chroma;
    }
    else if (hprime < 6) {
        r_1 = chroma;
        g_1 = 0;
        b_1 = x;
    }

    var m = v - chroma;
    var r = r_1 + m;
    var g = g_1 + m;
    var b = b_1 + m;

    return [r, g, b]
};
        

var colorSchemes = {};

// first few ported from Tile.java
// This is the new color30
colorSchemes.color30 = function (value) {
    var opacity = 96;
    if (i >= 150)
        return [0, 0, 0, opacity];
    var v = 0.8;
    
    var s;
    if (value % 30 < 28)
        s = (value % 30) * .037;
    else
        s = (30 - (value % 30)) * .333;

    var h;
    if (value < 30)
        h = 120; // green
    else if (value < 60)
        h = 240; // blue
    else if (value < 90)
        h = 52; // yellow
    else if (value < 120)
        h = 0; // red
    else {
        h = 0;
        s = 0;
        v = (29 - (value - 120)) * .0172;
    }

    var rgb = HSVtoRGB(h, s, v);
    return [rgb[0]*255, rgb[1]*255, rgb[2]*255, opacity];
};

colorSchemes.difference = function (val) {
    if (val <= 117) {
        // i = 117 - val algebraically
        return [0, (117 - val) * 2, 0, (117 - val) * 2 + 64];
    }
    else if (val >= 137) {
        return [(val - 117) * 2, 0, 0, (val - 117) * 2 + 64];
    }
    else return [0, 0, 0, 64];
};

colorSchemes.transparent = function (val) {
    if (val < 60)
        return [0, 0, 0, 240 - val * 4];
    else return [0, 0, 0, 0];
};

colorSchemes.mask = function (val) {
    var max = 90;
    if (val < max)
        return [0, 0, 0, val * 210 / 90];
    else
        return [0, 0, 0, 210];
};

colorSchemes.log = function (val) {
    // natural log scale, 5.5413 ~= ln 255
    return [0, 0, 0, (Math.log(val)/5.5413)*255];
};

var analystUrl = "/opentripplanner-api-webapp/ws/tile/{z}/{x}/{y}.png"; 
var analystLayerOpts = {url: analystUrl + buildQuery(params), style: 'mask'};
var analystLayer = new L.TileLayer.Canvas();

// Draw a single tile
analystLayer.drawTile = function (canvas, tileCoord, zoom) {
    // load the image from the server
    var rawTile = new Image();
    rawTile.src = analystLayerOpts.url
        .replace('{z}', zoom)
        .replace('{x}', tileCoord.x)
        .replace('{y}', tileCoord.y);

    // Once the image has loaded, we can manipulate it
    rawTile.onload = function () {

        var ctx = canvas.getContext('2d');
        // First draw the image, then modify it
        ctx.drawImage(rawTile, 0, 0);

        // now, transform
        imageData = ctx.getImageData(0, 0, 256, 256);
        
        // read the value at each pixel and rewrite it
        for (var i = 0; i < 256 * 256; i++) {
            // get the red value; all the values should be the same for gray
            var red = imageData.data[i * 4];
            var rgba = colorSchemes[analystLayerOpts.style](red);

            // write the values in, rgba
            imageData.data[i * 4] = rgba[0];
            imageData.data[i * 4 + 1] = rgba[1];
            imageData.data[i * 4 + 2] = rgba[2];
            imageData.data[i * 4 + 3] = rgba[3];
        }
        // write it back
        ctx.putImageData(imageData, 0, 0);
    }
}

// draw the legend for the named style
var drawLegend = function (style) {
    var legend = document.getElementById('legend');
    var ctx = legend.getContext('2d');
    
    // This will all be overwritten; current content does not matter
    var singleRow = ctx.getImageData(0, 0, 256, 1);
    
    // fill in one row, copying bytes
    for (var i = 0; i < 256; i++) {
        var rgba = colorSchemes[style](i);
        singleRow.data[i * 4] = rgba[0];
        singleRow.data[i * 4 + 1] = rgba[1];
        singleRow.data[i * 4 + 2] = rgba[2];
        singleRow.data[i * 4 + 3] = rgba[3];
    }

    // Now, copy that row into the canvas 50 times
    for (var i = 0; i < 50; i++) {
        ctx.putImageData(singleRow, 0, i);
    }
}

var refresh = function () {
	var o = origMarker.getLatLng();
	var d = destMarker.getLatLng();
	console.log(flags);
    if (flags.twoEndpoint) {
    	params.fromPlace = [o.lat + "," + o.lng, d.lat + "," + d.lng];
    	// toPlace is not currently used, but must be provided to avoid missing vertex error
    	// missing to place should be tolerated in batch mode
    	params.toPlace = [d.lat + "," + d.lng, o.lat + "," + o.lng];
    	map.addLayer(destMarker);
    } else {
    	params.fromPlace = o.lat + "," + o.lng;
    	// toPlace is not currently used, but must be provided to avoid missing vertex error
    	params.toPlace = d.lat + "," + d.lng;
    	map.removeLayer(destMarker);
    }
    console.log(params);
    console.log(analystUrl + buildQuery(params));
    analystLayerOpts.url = analystUrl + buildQuery(params);
    analystLayer.redraw();

    drawLegend(analystLayerOpts.style);
};

// create geoJSON layers for DC Purple Line

var purpleLineCoords = [
 [-77.094111, 38.984299],
 [-77.077117, 38.994373],
 [-77.053857, 39.000076],
 [-77.039909, 38.999709],
 [-77.031970, 38.994172],
 [-77.024503, 38.995340],
 [-77.017379, 38.999209],
 [-77.009525, 38.999776],
 [-77.003560, 38.998141],
 [-76.995492, 38.999509],
 [-76.987596, 38.989036],
 [-76.978927, 38.983665],
 [-76.955194, 38.985466], 
 [-76.944122, 38.987535],
 [-76.936870, 38.985567],
 [-76.927128, 38.978094],
 [-76.925197, 38.968785],
 [-76.920004, 38.960543],
 [-76.900392, 38.963246],
 [-76.885371, 38.951733],
 [-76.871681, 38.947928]];

var purpleLineStopsFeature = { 
	"type": "Feature",
	"geometry": {
	    "type": "MultiPoint",
	    "coordinates": purpleLineCoords,
	    "properties": {
	        "name": "Purple Line stops"
	    }	
	}
};
var geojsonMarkerOptions = {
		radius: 4,
		fillColor: "#000",
		color: "#000",
		weight: 0,
		opacity: 0,
		fillOpacity: 0.8
};
var purpleLineStopsLayer = new L.GeoJSON(purpleLineStopsFeature, {
	pointToLayer: function (latlng) { 
		return new L.CircleMarker(latlng, geojsonMarkerOptions);
	}});
map.addLayer(purpleLineStopsLayer);

var purpleLineAlignmentFeature = { 
	"type": "Feature",
	"geometry": {
	    "type": "LineString",
	    "coordinates": purpleLineCoords,
	    "properties": {
	        "name": "Purple Line alignment",
	        "style": {
	            "color": "#004070",
	            "weight": 4,
	            "opacity": 0.8
	        }
	    }	
	}
};
var purpleLineAlignmentLayer = new L.GeoJSON(purpleLineAlignmentFeature);
map.addLayer(purpleLineAlignmentLayer);

var baseMaps = {
	"MapBox": mapboxLayer,
    "OSM": osmLayer,
    "Aerial Photo": aerialLayer
};
	        
var overlayMaps = {
    "Analyst Tiles": analystLayer,
    "Stops": purpleLineStopsLayer,
	"Alignment": purpleLineAlignmentLayer
};

var initLocation = INIT_LOCATION;
if (AUTO_CENTER_MAP) {
	// attempt to get map metadata (bounds) from server
	var request = new XMLHttpRequest();
	request.open("GET", "/opentripplanner-api-webapp/ws/metadata", false); // synchronous request
	request.setRequestHeader("Accept", "application/xml");
	request.send(null);
	if (request.status == 200 && request.responseXML != null) {
		var x = request.responseXML;
		var minLat = parseFloat(x.getElementsByTagName('minLatitude')[0].textContent);
		var maxLat = parseFloat(x.getElementsByTagName('maxLatitude')[0].textContent);
		var minLon = parseFloat(x.getElementsByTagName('minLongitude')[0].textContent);
		var maxLon = parseFloat(x.getElementsByTagName('maxLongitude')[0].textContent);
		var lon = (minLon + maxLon) / 2;
		var lat = (minLat + maxLat) / 2;
		initLocation = new L.LatLng(lat, lon);
	}
}
map.setView(initLocation, 12);
var initLocation2 = new L.LatLng(initLocation.lat + 0.05, initLocation.lng + 0.05);
console.log(initLocation, initLocation2);
var greenMarkerIcon = new L.Icon({
    iconUrl: 'js/lib/leaflet/images/marker-green.png',
});
var redMarkerIcon = new L.Icon({
    iconUrl: 'js/lib/leaflet/images/marker-red.png',
});
var origMarker = new L.Marker(initLocation,  {draggable: true, icon: greenMarkerIcon });
var destMarker = new L.Marker(initLocation2, {draggable: true, icon: redMarkerIcon });
origMarker.on('dragend', refresh);
origMarker.bindPopup("I am the origin.");
destMarker.on('dragend', refresh);
destMarker.bindPopup("I am the destination.");

map.addLayer(mapboxLayer);
map.addLayer(origMarker);
map.addLayer(analystLayer);

var layersControl = new L.Control.Layers(baseMaps, overlayMaps);
map.addControl(layersControl);

// tools

var purpleOn = function () {
    params.bannedRoutes = "";
    refresh();
};

var purpleOff = function () {
    params.bannedRoutes = "Test_Purple";
    refresh();
};

var color30 = function () {
    params.layers = 'traveltime',
    analystLayerOpts.style = 'color30',
    params.time = flags.startTime;
    flags.twoEndpoint = false;
    refresh();
};

var gray = function () {
    params.layers = 'traveltime',
    analystLayerOpts.style = 'mask',
    params.time = flags.startTime;
    flags.twoEndpoint = false;
    refresh();
};

var difference = function () {
    params.layers = 'difference',
    analystLayerOpts.style = 'difference',
    params.bannedRoutes = ["", "Test_Purple"];
    params.time = flags.startTime;
    flags.twoEndpoint = false;
    refresh();
};

var hagerstrand = function () {
	params.layers = 'hagerstrand',
	analystLayerOpts.style = 'transparent',
    params.bannedRoutes = "";
	params.time = [flags.startTime, flags.endTime],
	flags.twoEndpoint = true;
    refresh();
};

var mapSetupTool = function () {
    var o = document.getElementById('setupTime').value;
    if (o != '')
        flags.startTime = o;

    var d = document.getElementById('setupTime2').value;
    if (d != '')
        flags.endTime = d;

    var m = document.getElementById('setupMode').value;
    if (m != '')
        params.mode = m;

    var maxD = document.getElementById('setupMaxDistance').value;
    if (maxD != '')
        params.maxWalkDistance = maxD;

    // set time
    if (flags.twoEndpoint)
        params.time = [flags.startTime, flags.endTime];
    else
        params.time = flags.startTime;

    refresh();
    return false;
};     

var downloadTool = function () { 
    var dlParams = {
        format: document.getElementById('downloadFormat').value,
        srs: document.getElementById('downloadProj').value,
        resolution: document.getElementById('downloadResolution').value
    };

    // TODO: this bounding box needs to be reprojected!
    var bounds = map.getBounds();
    var bbox;

    // reproject
    var src = new Proj4js.Proj('EPSG:4326');
    // TODO: undefined srs?
    var dest = new Proj4js.Proj(dlParams.srs);

    // wait until ready then execute
    var interval;
    interval = setInterval(function () {
        // if not ready, wait for next iteration
        if (!(src.readyToUse && dest.readyToUse))
            return;

        // clear the interval so this function is not called back.
        clearInterval(interval);

        var swll = bounds.getSouthWest();
        var nell = bounds.getNorthEast();
        
        var sw = new Proj4js.Point(swll.lng, swll.lat);
        var ne = new Proj4js.Point(nell.lng, nell.lat);

        Proj4js.transform(src, dest, sw);
        Proj4js.transform(src, dest, ne);

        // left, bot, right, top
        bbox = [sw.x, sw.y, ne.x, ne.y].join(',');

        var url = '/opentripplanner-api-webapp/ws/wms' +
            buildQuery(params) +
            '&format=' + dlParams.format + 
            '&srs=' + dlParams.srs +
            '&resolution=' + dlParams.resolution +
            '&bbox=' + bbox;
            // all of the from, to, time, &c. is taken care of by buildQuery.
        
        window.open(url);
    }, 1000); // this is the end of setInterval, run every 1s

    // prevent form submission
    return false;
};

// read setup values from map setup tool on page load
mapSetupTool();

var baseDate = Date.parse("2012-06-06"); 
var msecPerHour = 60 * 60 * 1000;
var setOriginTime = function(fractionalHours) {
	var seconds = baseDate + fractionalHours * msecPerHour; 
	setupTime.value = new Date(seconds).toISOString().substring(0,19);
	setDestinationTime(setupRelativeTime2.value);
};
var setDestinationTime = function(fractionalHours) {
	var seconds = Date.parse(setupTime.value) + fractionalHours * msecPerHour; 
	setupTime2.value = new Date(seconds).toISOString().substring(0,19);
};

