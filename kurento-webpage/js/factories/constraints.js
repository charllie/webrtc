
app.factory('constraints', ['deviceDetector', function(device) {

	var type = 'composite';
	var browser = device.browser;
	var chromeExtensionInstalled = false;
	var canPresent = (device.isDesktop() && (browser == 'chrome' || browser == 'firefox'));
	
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
			// TODO

		}

		return constraints;

	};

	var getCurrent = function() {
		return type;
	};

	var setCurrent = function(t) {
		type = t;
	};

	var setId = function(id) {
		chromeConstraintPresentation.video.mandatory.chromeMediaSourceId = id;
	};

	var chromeExtensionDetected = function() {
		chromeExtensionInstalled = true;
	};

	return {
		browserIsChrome: (browser == 'chrome'),
		chromeExtensionDetected: chromeExtensionDetected,
		setId: setId,
		getCurrent: getCurrent,
		setCurrent: setCurrent,
		get: get
	};
}]);