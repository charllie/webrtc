app.factory('constraints', ['$window', 'deviceDetector', 'upload', function($window, device, upload) {

	var type = 'composite';
	var browser = device.browser;
	var chromeExtensionInstalled = false;
	var canPresent = (device.isDesktop() && (browser == 'chrome' || browser == 'firefox')) && ($window.location.protocol == 'https:');
	
	var constraintWebcam = {
		audio: true,
		video: {
			width: { min: 160, ideal: 160, max: 160 },
			height: { min: 120, ideal: 120, max: 120 }
		}
	};

	var defaultConstraintPresentation = {
		audio: false,
		video: {
			width: 320,
			height: 180
		}
	};

	var chromeConstraintPresentation = {
		audio: false,
		video: {
			mandatory: {
				chromeMediaSource: 'desktop',
				maxWidth: window.screen.width,
				maxHeight: window.screen.height
			}
		}
	};

	var get = function() {

		var constraints;

		if (type != 'composite' && canPresent) {

			if (browser == 'chrome') {
				constraints = chromeConstraintPresentation;
			} else {
				constraints = defaultConstraintPresentation;
				constraints.video.mediaSource = type;
			}

		} else {

			constraints = constraintWebcam;
			
			if (upload.speed >= 0.5) {
				consMaxWidth = 320;
				consMaxHeight = 240;
			}

			constraints.video.width.max = constraints.video.width.ideal = consMaxWidth;
			constraints.video.height.max = constraints.video.height.ideal = consMaxHeight;

		}

		return constraints;

	};

	var getType = function() {
		return type;
	};

	var setType = function(t) {
		type = t;
	};

	var setId = function(id) {
		chromeConstraintPresentation.video.mandatory.chromeMediaSourceId = id;
	};

	var chromeExtensionDetected = function() {
		chromeExtensionInstalled = true;
	};

	var isChromeExtensionInstalled = function() {
		return chromeExtensionInstalled;
	};

	return {
		browser: browser,
		browserIsChrome: (browser == 'chrome'),
		browserIsFirefox: (browser == 'firefox'),
		chromeExtensionDetected: chromeExtensionDetected,
		isChromeExtensionInstalled: isChromeExtensionInstalled,
		canPresent: canPresent,
		setId: setId,
		getType: getType,
		setType: setType,
		get: get
	};
}]);