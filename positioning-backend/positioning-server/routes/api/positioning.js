var express = require('express');
const cassandra = require('cassandra-driver');
const { body, check, validationResult } = require('express-validator');

var router = express.Router();

// Connect to cassandra database
const TimeUuid = cassandra.types.TimeUuid;
const cassandraClient = new cassandra.Client({
  contactPoints: ['cassandra:9042'],
  localDataCenter: process.env.CASSANDRA_DATACENTER,
  keyspace: 'positioning'
});
cassandraClient.connect(function (err) {
  if (err) {
    return console.error(err);
  }

  console.log('Connected to Cassandra cluster with %d host(s): %j, keyspace:',
    cassandraClient.hosts.length, cassandraClient.hosts.keys(), cassandraClient.keyspace);
});

const predictedCoordinatesTable = 'predicted_coordinates';
const checkpointsTable = 'checkpoint_timestamps';

/* GET all predicted coordinates. */
router.get('/', function(req, res, next) {
  const query = `SELECT toTimestamp(timeuuid), x, y, confidence FROM ${predictedCoordinatesTable}`;
  cassandraClient.execute(query)
    .then(result => res.json(result.rows))
    .catch(error => res.status(500).json(error));
});

/* GET all checkpoint timestamps. */
router.get('/checkpoints', function(req, res, next) {
  const query = `SELECT toTimestamp(timeuuid), checkpoint FROM ${checkpointsTable}`;
  cassandraClient.execute(query)
    .then(result => res.json(result.rows))
    .catch(error => res.status(500).json(error));
});

/* POST new predicted coordinates. */
router.post(
  '/',
  body('timestamp').isInt(),
  body('x').isDecimal(),
  body('y').isDecimal(),
  body('confidence').isDecimal(),
  function(req, res, next) {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      console.log({ errors: errors.array() });
      res.status(400).json({ errors: errors.array() });
      return;
    }

    const query = `INSERT INTO ${predictedCoordinatesTable} (timeuuid, x, y, confidence) VALUES (?, ?, ?, ?)`; 
    const timeuuid = TimeUuid.fromDate(new Date(req.body.timestamp));
    const params = { 
      "timeuuid": timeuuid,
      "x": req.body.x,
      "y": req.body.y,
      "confidence": req.body.confidence
    };

    console.log(`${timeuuid.getDate()} - Inserted new predicted coordinates`);
    
    cassandraClient.execute(query, params, { prepare: true })
      .then(_ => res.status(201).json(params))
      .catch(error => res.status(500).json({ error: error }));
  }
);

/* POST new checkpoint timestamp. */
router.post(
  '/checkpoints',
  body('timestamp').isInt(),
  body('checkpoint').isInt(),
  function(req, res, next) {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      console.log({ errors: errors.array() });
      res.status(400).json({ errors: errors.array() });
      return;
    }

    const query = `INSERT INTO ${checkpointsTable} (timeuuid, checkpoint) VALUES (?, ?)`; 
    const timeuuid = TimeUuid.fromDate(new Date(req.body.timestamp));
    const params = { 
      "timeuuid": timeuuid,
      "checkpoint": req.body.checkpoint
    };

    console.log(`${timeuuid.getDate()} - Inserted new checkpoint timestamp`);
    
    cassandraClient.execute(query, params, { prepare: true })
      .then(_ => res.status(201).json(params))
      .catch(error => res.status(500).json({ error: error }));
  }
);

/* DELETE all predicted coordinates. */
router.delete('/', function(req, res, next) {
  const query = `TRUNCATE ${predictedCoordinatesTable}`;
  cassandraClient.execute(query)
    .then(_ => res.sendStatus(204))
    .catch(error => res.status(500).json(error));
});

/* DELETE all checkpoint timestamps. */
router.delete('/checkpoints', function(req, res, next) {
  const query = `TRUNCATE ${checkpointsTable}`;
  cassandraClient.execute(query)
    .then(_ => res.sendStatus(204))
    .catch(error => res.status(500).json(error));
});

module.exports = router;