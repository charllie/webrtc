app.factory('socket', ['$window', function($window) {

	var uri;
	var socket = {
		readyState: 0
	};

	$.get('/config.json', function(data) {
		uri = ($window.location.protocol == 'https:') ? data.wss_uri : data.ws_uri;
		socket = new WebSocket(uri);
	});

	var send = function(message) {
		var jsonMessage = JSON.stringify(message);
		console.log('Sending message: ' + jsonMessage);
		socket.send(jsonMessage);
	};

	var get = function() {
		return socket;
	};

	var isOpen = function() {
		return (socket.readyState == 1);
	};

	$window.onbeforeunload = function() {
		send({ id: 'leaveRoom' });
		socket.close();
	};

	return {
		send: send,
		get: get,
		isOpen: isOpen
	};
}]);