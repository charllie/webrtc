var app = angular.module('app', ['ngRoute', 'ng.deviceDetector', 'lumx']);

app.config(['$routeProvider', function($routeProvider) {
	$routeProvider
		.when('/', {
			templateUrl: 'views/login.html',
			controller: 'UserCtrl'
		})
		.when('/rooms/:roomName', {
			templateUrl: 'views/room.html',
			controller: 'RoomCtrl'
		})
		.otherwise({
			redirectTo: '/'
		});
}]);

// Injections
app.controller('UserCtrl', ['$scope', '$location', 'socket', 'participants', UserCtrl]);
app.controller('RoomCtrl', ['$scope', '$location', '$routeParams', 'socket', 'constraints', 'participants', RoomCtrl]);