app.factory('variables', ['$http', function($http) {

	var get = function() {
		return $http.get('/config.json').then(function(result) {
			return result.data;
		});
	};

	return { get: get };
}]);