function upload(uploadSize, startTime) {
	$.post('/upload', { data: "0".repeat(uploadSize) }, function(data) {
		var endTime = Date.now();
		var diffTime = endTime - startTime;

		var speed = uploadSize / diffTime;

		console.log(speed);
	});
}