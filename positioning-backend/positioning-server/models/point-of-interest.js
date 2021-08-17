const mongoose = require('mongoose');

const PointOfInterestSchema = mongoose.Schema({
    name: { type: String, required: true },
    description: String,
    coordinates: {
        type: {
            x: { type: Number, required: true },
            y: { type: Number, required: true }
        },
        required: true
    },
    radius: { type: Number, required: true }
});

module.exports = PointOfInterestSchema;
