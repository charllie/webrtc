var app = angular.module('app', ['ngRoute', 'angular-storage']);

app.config(['$routeProvider', function($routeProvider) {
	$routeProvider
		.when('/', {
			templateUrl: 'views/login.html',
			controller: 'UserCtrl'
		})
		.when('/sip', {
			templateUrl: 'views/sip.html',
			controller: 'SIPCtrl'
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

app.controller('SIPCtrl', ['$scope', 'aiStorage', function($scope, store) {
	var user = store.get('user');
	$scope.user = user;

	$scope.session = null;

	var endButton = document.getElementById('endCall').addEventListener("click", function() {
		$scope.session.bye();
		alert("Call Ended");
	}, false);

	var configuration = {
	    uri: user.uri,
	    authorizationUser: user.id,
	    password: user.pwd,
	    wsServers: user.wsServer,
	    stunServers: ["stun:stun.l.google.com:19302"],
	};

	var options = {
		media: {
			constraints: {
				audio: true,
				video: false
			},
			render: {
				remote: document.getElementById('remoteAudio'),
				local: document.getElementById('localAudio')
			}
		}
	};

	var userAgent = new SIP.UA(configuration);

	userAgent.on('invite', function(session) {
		session.accept(option);
	});

	$scope.call = function(person) {
		userAgent.invite('sip:' + person.id + '@' + user.server, options);
	}
}]);