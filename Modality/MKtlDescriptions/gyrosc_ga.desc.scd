// // gyrosc iphone/ipad app
// has lots of movement sensors:
// gyro, accel, comp, matrix, rrate, quat, grav, mag, gps
// implemented as example: gyro and accel,
// others can be added later as needed.

// for using gyrosc nametag ID concept, see note below.

/*
o = OSCMonitor.new;
o.enable;
// which sending addresses?
o.anaDict.msgNamesByAddr.keys.postcs
// which msgNames from where?
o.anaDict.msgNamesByAddr
// the last n messages from all addresses
o.anaDict.messagesByAddr.postcs
o.disable;

// or rawer:
OSCFunc.trace;
OSCFunc.trace(false);


g = MKtl(\gyr, "gyrosc*");
// update with the address seen in OSCMonitor or OSCFunc.trace:
g.device.updateSrcAddr("192.168.178.53", 60329);

g.trace;
g.trace(false);
[ /gyrosc/ipport, 192.168.178.53, 6449 ]
*/

(
deviceName: "gyrosc",
protocol: \osc,
deviceType: \phoneApp,
elementTypes: [\imu, \accelerometer],
status: (
	linux: "unknown",
	osx: "tested and working, subset of sensors only.",
	win: "unknown"),

idInfo: "gyrosc",
netAddrInfo: ( srcPort: 12345, recvPort: 57120 ),

//	gps: lat, long, alt, course, speed, time
//	ipport: ip, port
// receiving: /reorient, /vibrate

specs: (
	gyroAxis: [-pi, pi],
	accelAxis: [-3, 3],
	compass: [0, 360],
	rotMatAxis: \bipolar,
	rotRateAxis:  [-3, 3],
	quaternionAxis: \bipolar,
	gravityAxis:  \bipolar,
	magFieldAxis: [-50, 50],
),

deviceInfo: (
	vendorURI: "http://www.bitshapesoftware.com/instruments/gyrosc/",
	// manualURI: "",
	// description: "",
	// features: [],
	// notes: "",
	// hasScribble: false
),

collectives: (
	gyro: (
		oscPath: '/gyrosc/gyro',
		elements: [[\gyro, \pitch], [\gyro, \roll], [\gyro, \yaw]],
		ioType: \in
	),
	accel: (
		oscPath: '/gyrosc/accel',
		elements: [ [\accel, \x], [\accel, \y], [\accel, \z]],
		ioType: \in
	)
	// similar for comp, matrix, rrate, quat, grav, mag, gps
),
	// actually about 7 more messages possible.
	// replace wildcard later with nameTag as set in iphone used.
description: (
	\accel: (
		\x: (elementType: 'accelAxis', ioType: \collectiveIn, \spec: 'accelAxis' ),
		\y: (elementType: 'accelAxis', ioType: \collectiveIn, \spec: 'accelAxis' ),
		\z: (elementType: 'accelAxis', ioType: \collectiveIn, \spec: 'accelAxis' )
	),
	\gyro: (
		\pitch: (elementType: 'accelAxis', ioType: \collectiveIn, \spec: 'gyroAxis' ),
		\roll: (elementType: 'accelAxis', ioType: \collectiveIn, \spec: 'gyroAxis' ),
		\yaw: (elementType: 'accelAxis', ioType: \collectiveIn, \spec: 'gyroAxis' )
	)
	// similar for comp, matrix, rrate, quat, grav, mag, gps
)
)

///////////////
// gyrosc provision for nametagging is tricky to integrate in MKtl / OSC:
// gyrosc identifies the phone it runs on by replacing an asterisk
// in all control element message names, so that:
// // 'gyrosc/*/gyro' becomes e.g. 'gyrosc/mytag/gyro'
//
// This would require modifying the desc by replacing the '*' in all oscpaths,
// or creating custom inverse matching responders that match for the
// inserted nametag inside the response func.
// To be done later if really needed.