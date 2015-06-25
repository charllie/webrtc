var session;

var endButton = document.getElementById('endCall').addEventListener("click", function() {
	session.bye();
	alert("Call Ended");
}, false);

/*
var userAgent = new SIP.UA({
	uri: "sip:ntmudo@178.62.211.128",
	wsServers: ["ws://178.62.211.128:8080/ws"],
	password: "NP5DAB",
	register: false,
	stunServers: ["stun:stun.l.google.com:19302"],
	traceSip: true
});
*/

var configuration = {
    uri: 'sip:h7u6y0@178.62.211.128',
    authorizationUser: 'h7u6y0',
    password: 'GJUE5X',
    wsServers: ["ws://178.62.211.128:8080/ws"],
    stunServers: ["stun:stun.l.google.com:19302"],
};

var userAgent = new SIP.UA(configuration);

userAgent.on('invite', function (session) {
    session.accept({
    	media: {
    		constraints: {
    			audio: true,
    			video: false
    		},
    		render: {
    			remote: document.getElementById('remoteAudio'),
    			local: document.getElementById('localAudio')
    		}
    	}
    });
});