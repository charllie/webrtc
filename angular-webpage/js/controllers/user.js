function UserCtrl($scope, $location, socket, constraints, notifications, participants) {

	$scope.participant = {
		name: '',
		room: '',
		compositeOptions: 'normal'
	};

	$scope.color = 'blue-grey';

	$scope.checked = {
		name: true,
		room: true
	};

	$scope.isIncompatible = function() {
		var browser = constraints.browser;

		if (browser == 'safari' || browser == 'ie' || browser == 'ms-edge')
			return true;

		return false;
	};

	$scope.join = function(participant) {

		if (_.isEmpty(participant.name) || _.isEmpty(participant.room)) {

			$scope.checked.name = !_.isEmpty(participant.name);
			$scope.checked.room = !_.isEmpty(participant.room);

			$scope.color = 'red';

		} else {

			if (socket.isOpen()) {

				var userId = createGuid();
				participants.add(userId, participant.name);

				socket.prepareJoiningRoom({
					id: 'joinRoom',
					userId: userId,
					name: participant.name,
					room: participant.room,
					mediaSource: 'composite'
				});

				constraints.setCompositeOptions(participant.compositeOptions);

				$location.path("/rooms/" + participant.room);

			} else {

				var warning = {
					title: 'Websocket Error',
					content: 'Unable to connect to the server. Please try later.'
				};

				notifications.alert(warning.title, warning.content, 'Ok', function(answer) {
					// This should be handled by lumx (it must be a bug)
					// May be removed in the future
					$('.dialog-filter').remove();
					$('.dialog').remove();
				});

			}
		}
	};

	if (constraints.getWarning()) {

		var warning = {
			title: 'Username already taken',
			content: 'Please choose another username.'
		};

		notifications.alert(warning.title, warning.content, 'Ok', function(answer) {
			// This should be handled by lumx (it must be a bug)
			// May be removed in the future
			$('.dialog-filter').remove();
			$('.dialog').remove();
		});

		constraints.setWarning(null);

	}
}

function createGuid() {
	return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
		var r = Math.random()*16|0, v = c === 'x' ? r : (r&0x3|0x8);
		return v.toString(16);
	});
}