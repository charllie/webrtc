app.factory('participants', ['socket', function(socket) {

	var participants = {};
	var id = null;
	var name = null;

	function add(userId, n) {

		var participant = {
			userId: userId,
			name: n,
			rtcPeer: {
				presentation: null,
				composite: null
			},

			offer: function(type, error, offerSdp, wp) {
				if (error)
					return console.error("sdp offer error");

				console.log('Invoking SDP offer callback function');

				var msg = {
					id: "receiveVideoFrom",
					userId: userId,
					sender: n,
					sdpOffer: offerSdp,
					type: type
				};

				socket.send(msg);
			},

			offerToReceive: {
				composite: function(error, offerSdp, wp) {
					this.offer('composite', error, offerSdp, wp);
				},
				presentation: function(error, offerSdp, wp) {
					this.offer('presentation', error, offerSdp, wp);
				}
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

			onIceCandidate: {
				composite: function(candidate, wp) {
					this.iceCandidate('composite', candidate, wp);
				},

				presentation: function(candidate, wp) {
					this.iceCandidate('presentation', candidate, wp);
				}
			},

			disposeType: function(type) {
				if (this.rtcPeer[type])
					this.rtcPeer[type].dispose();
			},

			dispose: function() {
				console.log('Disposing participant ' + this.name);

				this.disposeType('presentation');
				this.disposeType('composite');
			},

			getIceCandidate: function(type) {

				if (type != 'composite')
					return this.onIceCandidatePresentation;
				return this.onIceCandidateComposite;
			}
		};

		Object.defineProperty(participant.rtcPeer, 'presentation', {
			writable: true
		});

		Object.defineProperty(participant.rtcPeer, 'composite', {
			writable: true
		});

		participants[userId] = participant;

		if (name === null) {
			id = userId;
			name = n;
		}
	}

	function get(userId) {
		return participants[userId];
	}

	function remove(userId) {
		delete participants[userId];
	}

	function me() {
		return participants[id];
	}

	function isEmpty() {
		return _.isEmpty(participants);
	}

	function clear() {
		for (var key in participants) {
			if (participants[key] !== undefined)
				participants[key].dispose();

			delete participants[key];
		}

		name = null;
		id = null;
	}

	return {
		add: add,
		clear: clear,
		get: get,
		isEmpty: isEmpty,
		me: me,
		remove: remove
	};
}]);