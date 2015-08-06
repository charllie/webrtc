app.factory('socket', function() {

	var socket = new WebSocket('wss://webrtc.ml/groupcall');

	var send = function(message) {
		var jsonMessage = JSON.stringify(message);
		console.log('Sending message: ' + jsonMessage);
		socket.send(jsonMessage);
	};

	var get = function() {
		return socket;
	};

	return {
		send: send,
		get: get,
		//onmessage: socket.onmessage,
		close: socket.close
	};
});