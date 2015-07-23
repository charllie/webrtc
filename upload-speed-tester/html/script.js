if (!String.prototype.repeat) {
	String.prototype.repeat = function(count) {
		"use strict";
		if (this === null)
			throw new TypeError("ne peut convertir " + this + " en objet");
		var str = "" + this;
		count = +count;
		if (count != count)
			count = 0;
		if (count < 0)
			throw new RangeError("le nombre de répétitions doit être positif");
		if (count === Infinity)
			throw new RangeError("le nombre de répétitions doit être inférieur à l'infini");
		count = Math.floor(count);
		if (str.length === 0 || count === 0)
			return "";
		// En vérifiant que la longueur résultant est un entier sur 31-bit
		// cela permet d'optimiser l'opération.
		// La plupart des navigateurs (août 2014) ne peuvent gérer des
		// chaînes de 1 << 28 caractères ou plus. Ainsi :
		if (str.length * count >= 1 << 28)
			throw new RangeError("le nombre de répétitions ne doit pas dépasser la taille de chaîne maximale");
		var rpt = "";
		for (;;) {
			if ((count & 1) == 1)
				rpt += str;
			count >>>= 1;
			if (count === 0)
				break;
			str += str;
		}
		return rpt;
	};
}

function upload(uploadSize) {
	var content = "0".repeat(uploadSize);
	var startTime, endTime;
	var iterations = 5;

	var speed = [];

	/*$.post('/upload', { data: content }, function(data) {
		var endTime = Date.now();
		var diffTime = endTime - startTime;

		var speed = 0.008 * uploadSize / diffTime;

		console.log(speed);
	});*/
	function beforeSendFunction() {
		startTime = Date.now();
	}

	function successFunction(data) {
		endTime = Date.now();
		speed.push(0.008 * uploadSize / (endTime - startTime));
	}

	for (var i = 0; i < iterations; i++) {
		$.ajax('/upload', {
			data: {
				content: content
			},
			type: 'POST',
			async: false,
			beforeSend: beforeSendFunction,
			success: successFunction
		});
	}

	console.log(speed);

}