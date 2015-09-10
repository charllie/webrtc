app.factory('constraints', ['$window', 'deviceDetector', 'upload', function($window, device, upload) {

	var type = 'composite';
	var viewOnly = false;
	var browser = device.browser;
	var chromeExtensionInstalled = false;
	var canPresent = (device.isDesktop() && (browser == 'chrome' || browser == 'firefox')) && ($window.location.protocol == 'https:');
	var warning = null;

	// Configuration for the extension if it is Chrome
	if (browser == 'chrome') {
		$window.addEventListener('message', function(event) {
			if (event.origin != $window.location.origin) return;

			// content-script will send a 'SS_PING' msg if extension is installed
			if (event.data.type && (event.data.type === 'SS_PING'))
				chromeExtensionInstalled = true;
		});
	}

	var emptyConstraint = {
		audio: false,
		video: false
	};

	var constraintWebcam = {
		audio: true,
		video: {
			width: { min: 160 },
			height: { min: 120 }
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

	function get() {

		var constraints;

		if (type != 'composite' && canPresent) {

			if (browser == 'chrome') {
				constraints = chromeConstraintPresentation;
			} else {
				constraints = defaultConstraintPresentation;
				constraints.video.mediaSource = type;
			}

		} else if (viewOnly) {

			constraints = emptyConstraint;

		} else {

			constraints = constraintWebcam;

			if (upload.speed() >= 0.5) {
				consMaxWidth = 320;
				consMaxHeight = 240;
			} else {
				consMaxWidth = 160;
				consMaxHeight = 120;
			}

			constraints.video.width.max = constraints.video.width.ideal = consMaxWidth;
			constraints.video.height.max = constraints.video.height.ideal = consMaxHeight;

		}

		return constraints;

	}

	function getType() {
		return type;
	}

	function setType(t) {
		type = t;
	}

	function setViewOnly(b) {
		viewOnly = b;
	}

	function setId(id) {
		chromeConstraintPresentation.video.mandatory.chromeMediaSourceId = id;
	}

	function chromeExtensionDetected() {
		chromeExtensionInstalled = true;
	}

	function isChromeExtensionInstalled() {
		return chromeExtensionInstalled;
	}

	function getWarning() {
		return warning;
	}

	function setWarning(w) {
		warning = w;
	}

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
		get: get,
		setViewOnly: setViewOnly,
		getWarning: getWarning,
		setWarning: setWarning
	};
}]);