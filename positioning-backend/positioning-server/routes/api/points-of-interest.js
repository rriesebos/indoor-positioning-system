var express = require('express');
const mongoose = require('mongoose');
const PointOfInterestSchema = require('../../models/point-of-interest');
const { body, check, validationResult } = require('express-validator');

var router = express.Router();

// Connect to mongoDB database
const mongoConnection = mongoose.createConnection(`mongodb://mongodb:27017/pointsOfInterest`, { useNewUrlParser: true, useUnifiedTopology: true });
mongoConnection.on('error', console.error.bind(console, 'MongoDB connection error:'));
mongoConnection.once('open', function() {
  console.log(`Connected to MongoDB 'pointsOfInterest' database`);
});
const PointOfInterest = mongoConnection.model('PointOfInterest', PointOfInterestSchema);

/* GET all points of interest. */
router.get('/', function(req, res, next) {
  PointOfInterest.find({})
    .then(result => res.json(result))
    .catch(error => res.status(500).json(error));
});

/* GET a single point of interest. */
router.get('/:id', function(req, res, next) {
  PointOfInterest.find({ _id: req.params.id }).exec()
    .then(result => res.json(result))
    .catch(error => res.status(500).json(error));
});

/* POST new point of interest. */
router.post(
  '/',
  body('name').notEmpty().withMessage("Empty value"),
  body('description').optional(),
  body('coordinates'),
  body('radius').isNumeric(),
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

    PointOfInterest.create(req.body)
      .then(result => res.status(201).json(result))
      .catch(error => res.status(500).json(error));
  }
);

/* PUT - update/create a point of interest. */
router.put(
  '/:id',
  body('name').notEmpty().withMessage("Empty value"),
  body('description').optional(),
  body('coordinates'),
  body('radius').isNumeric(),
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

    PointOfInterest.findOneAndUpdate({ _id: req.params.id }, req.body, { new: true, upsert: true, rawResult: true })
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

/* DELETE all points of interest. */
router.delete('/', function(req, res, next) {
  PointOfInterest.collection.drop()
    .then(_ => res.sendStatus(204))
    .catch(error => res.status(500).json(error));
});

/* DELETE a single point of interest. */
router.delete('/:id', function(req, res, next) {
  PointOfInterest.deleteOne({ _id: req.params.id })
    .then(_ => res.sendStatus(204))
    .catch(error => res.status(500).json(error));
});

module.exports = router;