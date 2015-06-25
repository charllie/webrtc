var app = angular.module('webRTCApp', ['ngRoute']);

app.config(function($routeProvider) {
	$routeProvider
		.when('/', {
			templateUrl: 'index.html'
		})
});
