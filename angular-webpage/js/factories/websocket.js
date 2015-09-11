app.factory('socket', ['$window', 'variables', function($window, variables) {

	var uri;
	var socket = {
		readyState: 0
	};
	var messagePrepared = null;

	variables.get().then(function(data) {
		uri = ($window.location.protocol == 'https:') ? data.wss_uri : data.ws_uri;
		start();
	});

	function start() {
		socket = new WebSocket(uri);

		socket.onopen = function(event) {
			setInterval(function() {
				send({ id: 'stay-alive' });
			}, 30000);
		};

		socket.onerror = function(error) {
			socket.close();
		};

		socket.onclose = function(event) {
			// Try to reconnect each 1 second
			setTimeout(function() {
				start();
			}, 1000);
		};
	}

	function send(message) {
		var jsonMessage = JSON.stringify(message);
		console.log('Sending message: ' + jsonMessage);
		try {
			socket.send(jsonMessage);
		} catch (e) {
			console.warn('Socket was closed before sending message');
		}
	}

	function get() {
		return socket;
	}

	function prepareJoiningRoom(m) {
		messagePrepared = m;
	}

	function roomReady() {
		if (messagePrepared !== null)
			send(messagePrepared);
		messagePrepared = null;
	}

	function isOpen() {
		return (socket.readyState == 1);
	}

	$window.onbeforeunload = function() {
		socket.close();
	};

	return {
		send: send,
		get: get,
		prepareJoiningRoom: prepareJoiningRoom,
		roomReady: roomReady,
		isOpen: isOpen
	};
}]);