function UserCtrl($scope, $location, socket, participants) {

	$scope.join = function(participant) {
		participants.add(participant.name);

		socket.send({
			id: 'joinRoom',
			name: participant.name,
			room: participant.room,
			mediaSource: 'webcam'
		});

		$location.path("/rooms/" + participant.room);
	};
}