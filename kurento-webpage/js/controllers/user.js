function UserCtrl($scope, $location, socket, participants) {

	$scope.participant = {
		name: '',
		room: ''
	};

	$scope.color = 'blue';

	$scope.checked = {
		name: true,
		room: true
	};

	$scope.join = function(participant) {

		if (_.isEmpty(participant.name) || _.isEmpty(participant.room)) {

			$scope.checked.name = !_.isEmpty(participant.name);
			$scope.checked.room = !_.isEmpty(participant.room);

			$scope.color = 'red';

		} else {
			participants.add(participant.name);

			socket.send({
				id: 'joinRoom',
				name: participant.name,
				room: participant.room,
				mediaSource: 'composite'
			});

			$location.path("/rooms/" + participant.room);
		}
	};
}