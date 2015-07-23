var express = require('express');
var app = express();
var bodyParser = require("body-parser");

var opts = {
	port: 8081,
	maxUploadSize: "20971520"
};

app.use(express.static(__dirname + '/html'));
app.use(express.limit(opts.maxUploadSize));
app.use(bodyParser.urlencoded({ extended: false }));

app.post('/upload', function(req, res) {
	res.end();
});

app.listen(opts.port);
