app.factory('participants', ['socket', function(socket) {

	var participants = {};
	var name = null;

	var add = function(n) {

		var participant = {
			name: n,
			rtcPeerPresentation: null,
			rtcPeerComposite: null,

			offer: function(type, error, offerSdp, wp) {
				if (error)
					return console.error("sdp offer error");

				console.log('Invoking SDP offer callback function');

				var msg = {
					id: "receiveVideoFrom",
					sender: n,
					sdpOffer: offerSdp,
					type: type
				};

				socket.send(msg);
			},

			iceCandidate: function(type, candidate, wp) {
				console.log("Local candidate" + JSON.stringify(candidate));

				var message = {
					id: 'onIceCandidate',
					candidate: candidate,
					type: type
				};

				socket.send(message);
			},

			offerToReceiveComposite: function(error, offerSdp, wp) {
				this.offer("composite", error, offerSdp, wp);
			},

			offerToReceivePresentation: function(error, offerSdp, wp) {
				this.offer("presentation", error, offerSdp, wp);
			},

			onIceCandidateComposite: function(candidate, wp) {
				this.iceCandidate("composite", candidate, wp);
			},

			onIceCandidatePresentation: function(candidate, wp) {
				this.iceCandidate("presentation", candidate, wp);
			},

			dispose: function() {
				console.log('Disposing participant ' + this.name);

				if (this.rtcPeerPresentation !== null && this.rtcPeerPresentation !== undefined)
					this.rtcPeerPresentation.dispose();

				if (this.rtcPeerComposite !== null && this.rtcPeerComposite !== undefined)
					this.rtcPeerComposite.dispose();
			},

			getRtcPeer: function(type) {
				if (type != 'webcam' && type != 'composite')
					return this.rtcPeerPresentation;
				return this.rtcPeerComposite;

			},

			setRtcPeer: function(type, rtcPeer) {
				if (type != 'webcam' && type != 'composite')
					this.rtcPeerPresentation = rtcPeer;
				else
					this.rtcPeerComposite = rtcPeer;
			},

			getIceCandidate: function(type) {
				if (type != 'webcam' && type != 'composite')
					return this.onIceCandidatePresentation;
				return this.onIceCandidateComposite;
			},

			getOffer: function(type) {
				if (type != 'webcam' && type != 'composite')
					return this.offerToReceivePresentation;
				return this.offerToReceiveComposite;
			}
		};

		Object.defineProperty(participant, 'rtcPeerPresentation', {
			writable: true
		});

		Object.defineProperty(participant, 'rtcPeerComposite', {
			writable: true
		});

		participants[n] = participant;

		if (name === null)
			name = n;
	};

	var get = function(name) {
		return participants[name];
	};

	var remove = function(name) {
		delete participants[name];
	};

	var me = function() {
		return participants[name];
	};

	return {
		add: add,
		get: get,
		remove: remove,
		me: me
	};
}]);