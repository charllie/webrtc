var app = angular.module('app', ['ngRoute', 'angular-storage']);

app.config(['$routeProvider', function($routeProvider) {
	$routeProvider
		.when('/', {
			templateUrl: 'views/login.html',
			controller: 'UserCtrl'
		})
		.when('/sip', {
			templateUrl: 'views/sip.html',
			controller: 'UserCtrl'
		})
		.otherwise({
			redirectTo: '/'
		});
}]);

app.controller('UserCtrl', ['$scope', '$location', 'aiStorage', function($scope, $location, store) {
	$scope.user = store.get('user');

	$scope.submit = function(user) {
		store.set('user', user);
		$location.path("/sip");
	}
}]);