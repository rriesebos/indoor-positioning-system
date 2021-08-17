const mongoose = require('mongoose');

const BeaconSchema = mongoose.Schema({
    beaconAddress: { type: String, required: true, index: true, unique: true },
    txPower: Number,
    coordinates: {
        x: { type: Number, required: true },
        y: { type: Number, required: true }
    }
});

module.exports = BeaconSchema;
