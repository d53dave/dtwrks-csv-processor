# File Transformer Service

This Scala/Akka Application is offers the file transformer service functionality for the Backend Java/Scala Developer Test.

As mentioned before, this is a "naked" Scala/Akka application that uses `Akka Streams` for data processing and `ScalikeJDBC` for persitence into an in-memory `H2`.

# Workflow

After starting, the application will create a reactive stream that consumes messages it receives from Kafka. 

When a message appears, the target file path is extracted, and an Actor is spawned that loads the file (via akka streams), processes the lines, batches them up and spawns a child actor per batch to do the DB batch writes.

# Unique Ids

Deduplication is supported using two different mechanisms, being backed by either a **Set** or a **Bloom Filter**. The duplicate check is determined by the config parameter `processor.bloomfilter.active=false` (see next section). 

# Assumptions

- The required date transformation assumes the dates in the CSV file are on the local time zone, and converts them to UTC
- Duplicates are filtered by Id on a *per-file basis*. The database schema has a separate Id as primary key.
- No empty fields are allowed. Lines that do not contain id, name and date are discared.

# Configuration

The following parameters are configurable

```properties
messagebroker.topic="dtwrks.test"
messagebroker.urls="localhost:9092"

processor.date.format="dd-MM-yyyy HH:mm:ss"

processor.bloomfilter.expectedCardinality=100000
processor.bloomfilter.falsepositiveRate=0.05
processor.bloomfilter.active=false

processor.csv.delimiter=","
processor.csv.truncateLineAfter=10000

processor.db.batchsize=100
processor.db.batchtime=500ms

akka {
loglevel = "WARNING"
}
```

The basepath prop is optional. If it is missing, the app will generate a random temporary folder and use it to store uploads.

# Running
Start the application using `sbt run` or build a docker image with `sbt docker` and spin it up with `docker run -i -t <hash>` (see [docker run documentation](https://docs.docker.com/engine/reference/run/) for more info).

If running in docker, make sure that the application can reach the address that Kafka is bound to (e.g. by specifying `--net="bridge"` and resolving the proper IP addresses, if everything is running on localhost)

# Tests
Run the test suite using `sbt test`. The test coverage is not very good, but it didn't have problems processing the provided test file:

![asd](https://image.ibb.co/eKfiGF/Screen_Shot_2017_02_14_at_22_09_21.png)
