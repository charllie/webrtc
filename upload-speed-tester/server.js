var express = require('express');
var app = express();
var bodyParser = require("body-parser");

var opts = {
	port: 8081,
	maxUploadSize: "20971520"
};

app.use(express.static(__dirname + '/html'));
app.use(bodyParser.urlencoded({ limit: '20MB', extended: true }));

app.post('/upload', function(req, res) {
	res.setHeader('Content-Type', 'application/json');
	res.send(JSON.stringify({}));
	res.end();
});

app.listen(opts.port);
