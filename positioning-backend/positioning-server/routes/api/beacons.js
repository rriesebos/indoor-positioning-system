var express = require('express');
const cassandra = require('cassandra-driver');
const mongoose = require('mongoose');
const BeaconSchema = require('../../models/beacon');
const { body, check, validationResult } = require('express-validator');

var router = express.Router();

// Connect to cassandra database
const TimeUuid = cassandra.types.TimeUuid;
const cassandraClient = new cassandra.Client({
  contactPoints: ['cassandra:9042'],
  localDataCenter: process.env.CASSANDRA_DATACENTER,
  keyspace: 'beacons'
});
cassandraClient.connect(function (err) {
  if (err) {
    return console.error(err);
  }

  console.log('Connected to Cassandra cluster with %d host(s): %j, keyspace:',
    cassandraClient.hosts.length, cassandraClient.hosts.keys(), cassandraClient.keyspace);
});
const measurementsTable = 'measurements_by_beacon';

// Connect to mongoDB database
const mongoConnection = mongoose.createConnection(`mongodb://mongodb:27017/beacons`, { useNewUrlParser: true, useUnifiedTopology: true });
mongoConnection.on('error', console.error.bind(console, 'MongoDB connection error:'));
mongoConnection.once('open', function() {
  console.log(`Connected to MongoDB 'beacons' database`);
});
const Beacon = mongoConnection.model('Beacon', BeaconSchema);

/* GET all beacons. */
router.get('/', function(req, res, next) {
  Beacon.find({})
    .then(result => res.json(result))
    .catch(error => res.status(500).json(error));
});

/* GET all beacon's RSSI measurements. */
router.get('/rssi', function(req, res, next) {
  const query = `SELECT beacon_address, toTimestamp(timeuuid), rssi, distance, channel FROM ${measurementsTable}`;
  cassandraClient.execute(query)
    .then(result => res.json(result.rows))
    .catch(error => res.status(500).json(error));
});

/* GET a single beacon. */
router.get('/:beaconAddress', function(req, res, next) {
  Beacon.find({ beaconAddress: req.params.beaconAddress }).exec()
    .then(result => res.json(result))
    .catch(error => res.status(500).json(error));
});

/* GET a single beacon's RSSI measurements. */
router.get('/:beaconAddress/rssi', function(req, res, next) {
  const query = `SELECT beacon_address, toTimestamp(timeuuid), rssi, distance, channel FROM ${measurementsTable} WHERE beacon_address = ? ORDER BY timeuuid`;
  cassandraClient.execute(query, [ req.params.beaconAddress ], { prepare: true })
    .then(result => res.json(result.rows))
    .catch(error => res.status(500).json(error));
});

/* POST new beacon. */
router.post(
  '/',
  body('beaconAddress').notEmpty().withMessage("Empty value"),
  body('txPower').optional().isInt(),
  body('coordinates').optional(),
  async function(req, res, next) {
    if (req.body.coordinates) {
      await check('coordinates.x').isInt().run(req);
      await check('coordinates.y').isInt().run(req);
    }

    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      res.status(400).json({ errors: errors.array() });
      return;
    }

    Beacon.create(req.body)
      .then(result => res.status(201).json(result))
      .catch(error => {
        if (error.name === 'MongoError' && error.code === 11000) {
          res.status(409).send(`Beacon with beaconAddress '${req.body.beaconAddress}' already exists`);
          return;
        }

        res.status(500).json(error)
      });
  }
);

/* POST new beacon RSSI measurement. */
router.post(
  '/:beaconAddress/rssi',
  body('timestamp').isInt(),
  body('rssi').isInt(),
  body('distance').isDecimal(),
  body('channel').isInt(),
  function(req, res, next) {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      res.status(400).json({ errors: errors.array() });
      return;
    }

    const query = `INSERT INTO ${measurementsTable} (beacon_address, timeuuid, rssi, distance, channel) VALUES (?, ?, ?, ?, ?)`; 
    const timeuuid = TimeUuid.fromDate(new Date(req.body.timestamp));
    const params = { 
      "beacon_address": req.params.beaconAddress,
      "timeuuid": timeuuid,
      "rssi": req.body.rssi,
      "distance": req.body.distance,
      "channel": req.body.channel
    };

    console.log(`${timeuuid.getDate()} - Inserted new measurement for ${req.params.beaconAddress}`);
    
    cassandraClient.execute(query, params, { prepare: true })
      .then(_ => res.status(201).json(params))
      .catch(error => res.status(500).json({ error: error }));
  }
);

/* PUT - update/create beacon. */
router.put(
  '/:beaconAddress',
  body('txPower').optional().isInt(),
  body('coordinates').optional(),
  async function(req, res, next) {
    if (req.body.coordinates) {
      await check('coordinates.x').isInt().run(req);
      await check('coordinates.y').isInt().run(req);
    }

    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      res.status(400).json({ errors: errors.array() });
      return;
    }

    Beacon.findOneAndUpdate({ beaconAddress: req.params.beaconAddress }, req.body, { new: true, upsert: true, rawResult: true })
      .then(result => {
        // Created new object, set status code to 201
        if (!result.lastErrorObject.updatedExisting) {
          res.status(201);
        }

        res.json(result.value);
      })
      .catch(error => res.status(500).json(error));
  }
);

/* DELETE all beacons. */
router.delete('/', function(req, res, next) {
  Beacon.collection.drop()
    .then(_ => res.sendStatus(204))
    .catch(error => res.status(500).json(error));
});

/* DELETE all rssi measurements. */
router.delete('/rssi', function(req, res, next) {
  const query = `TRUNCATE ${measurementsTable}`;
  cassandraClient.execute(query)
    .then(_ => res.sendStatus(204))
    .catch(error => res.status(500).json(error));
});

/* DELETE a single beacon. */
router.delete('/:beaconAddress', function(req, res, next) {
  Beacon.deleteOne({ beaconAddress: req.params.beaconAddress })
    .then(_ => res.sendStatus(204))
    .catch(error => res.status(500).json(error));
});

/* DELETE a single beacon's RSSI measurements. */
router.delete('/:beaconAddress/rssi', function(req, res, next) {
  const query = `DELETE FROM ${measurementsTable} WHERE beacon_address = ? IF EXISTS`;
  cassandraClient.execute(query, [ req.params.beaconAddress ], { prepare: true })
    .then(_ => res.sendStatus(204))
    .catch(error => res.status(500).json(error));
});

module.exports = router;