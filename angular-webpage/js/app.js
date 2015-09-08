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
app.controller('UserCtrl', ['$scope', '$location', 'socket', 'constraints', 'LxNotificationService', 'participants', UserCtrl]);
app.controller('RoomCtrl', ['$scope', '$location', '$window', '$routeParams', '$timeout', 'socket', 'constraints', 'LxNotificationService', 'LxProgressService', 'participants', RoomCtrl]);