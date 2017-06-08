//SocketIO NodeJS Group Chat Implementation Test
//Coded/Developed By Aravind.V.S
//www.aravindvs.com

// Modules
const app = require('express')();
const server = require('http').Server(app);
const io = require('socket.io').listen(server);
const moment = require('moment');
const async = require('async');
const Guid = require('guid');

// Models
const Coso = require('./model/coso');
const Phong = require('./model/phong');
const Dates = require('./model/date');
const Log = require('./model/log');

server.listen(9093, err => {
	console.log('Server is listening at port 9093');
});

// website localhost:9093 or 127.0.0.1:9093
app.get('/', function (req, res) {
	res.sendFile(__dirname + '/index.html');
});

io.sockets.on('connection', function (socket) {

	socket.on('server send download', function (data) {
		try {
			const json = JSON.parse(data);
			const source = json["source"];
			const target = json["target"];
			const id = json["id"];
			const name = json["name"];
			const begin = json["begin"];
			const end = json["end"];
			const from = json["from"];
			const to = json["to"];
			const days = json["days"];
			const objects = json["objects"];

			console.log(json);

			objects.forEach(function(item) {
				console.log("-------------------------------------------");
				console.log("Co so: ", item["coso"]);
				console.log("Phong: ", item["phong"]);

				const current = moment(Date.now()).format('YYYY-MM-DD HH:mm:ss');
				console.log("Time: ", current);
				console.log("Filename: ", source);

				const socketid = item["socketid"];
				const guid = Guid.raw();

				io.to(socketid).emit('download', {
					source: source, target: target, id: id, name: name, begin: begin,
					end: end, from: from, to: to, days: days,
					coso: item["coso"], phong: item["phong"],
					host: item["host"], path: item["path"], username: item["username"],
					password: item["password"], size: item["size"], token: guid
				});
			}, this);
		}
		catch (e) {
			console.log('Error: ', e.message);
		}
	});

	socket.on('error', function (err) {
		console.log("-------------------------------------------");

		const current = moment(Date.now()).format('YYYY-MM-DD HH:mm:ss');
		console.log("Time: ", current);
		console.log("Error: ", err);
	});
});

