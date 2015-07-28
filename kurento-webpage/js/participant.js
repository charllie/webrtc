/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 */

const PARTICIPANT_MAIN_CLASS = 'participant main';
const PARTICIPANT_CLASS = 'participant';

/**
 * Creates a video element for a new participant
 *
 * @param {String} name - the name of the new participant, to be used as tag
 *                        name of the video element.
 *                        The tag of the new element will be 'video<name>'
 * @return
 */
function Participant(name) {
	this.name = name;
	var rtcPeerPresentation;
	var rtcPeerComposite;

	function offer(type, error, offerSdp, wp) {
		if (error)
			return console.error("sdp offer error");

		console.log('Invoking SDP offer callback function');

		var msg = {
			id: "receiveVideoFrom",
			sender: name,
			sdpOffer: offerSdp,
			type: type
		};

		sendMessage(msg);
	}

	function iceCandidate(type, candidate, wp) {
		console.log("Local candidate" + JSON.stringify(candidate));

		var message = {
			id: 'onIceCandidate',
			candidate: candidate,
			type: type
		};

		sendMessage(message);
	}

	this.offerToReceiveComposite = function(error, offerSdp, wp) {
		offer("composite", error, offerSdp, wp);
	};

	this.offerToReceivePresentation = function(error, offerSdp, wp) {
		offer("presentation", error, offerSdp, wp);
	};


	this.onIceCandidateComposite = function(candidate, wp) {
		iceCandidate("composite", candidate, wp);
	};

	this.onIceCandidatePresentation = function(candidate, wp) {
		iceCandidate("presentation", candidate, wp);
	};

	Object.defineProperty(this, 'rtcPeerPresentation', {
		writable: true
	});

	Object.defineProperty(this, 'rtcPeerComposite', {
		writable: true
	});

	this.dispose = function() {
		console.log('Disposing participant ' + this.name);

		if (this.rtcPeerPresentation !== null && this.rtcPeerPresentation !== undefined)
			this.rtcPeerPresentation.dispose();

		if (this.rtcPeerComposite !== null && this.rtcPeerComposite !== undefined)
			this.rtcPeerComposite.dispose();
		//container.parentNode.removeChild(container);
	};
}