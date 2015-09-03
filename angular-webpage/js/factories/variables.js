app.factory('variables', ['$http', function($http) {

	function get() {
		return $http.get('config.json').then(function(result) {
			return result.data;
		});
	}

	return { get: get };
}]);