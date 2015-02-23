# clj-migrate

SoundCloud's database migration framework that uses clj files as migrations.

This allows us to use clojure.java.jdbc to actually run migrations and perform
any data-munging in clojure that may be required as part of the migration. You
don't need to write any stored procedures this way and everything is where its
supposed to be.

Based on ideas from https://github.com/bayan/kapooya. Theirs is tied to postgres
but we use mysql in our own special way.

Entry points:

1. migrate - run the database migrations
2. create  - create a skeleton clj migration file - timestamps are hard ;)

Each migration file has an (up) function and a (down) function. Up function is
invoked to apply the migration, and the down function is invoked to rollback
the migration. Each of these functions is passed the database connection
parameters required for clojure.java.jdbc to work properly.

NOTE: As they are right now, down migrations are simply here for completeness
and should NOT be used in production without a really good reason, since this
framework runs ALL the down migrations in the migrations-dir. If you do not
want a down migration to be run please make sure the file is NOT present in
the migrations-dir. We still need to decide if we ever want to rollback a
single migration during automated deployment via a build pipeline. Our
current approach is to roll-forward with a new migration.

## Usage

    Usage: lein run [options] action [create migration name]
       Or: java -jar clj-migrate-standalone.jar [options] action [create migration name]

    Options:
      -j, --url JDBC_URL                   Database connection URL
      -u, --username USERNAME              Database connection username
      -p, --password PASSWORD              Database connection password
      -d, --dir DIRECTORY      migrations  Directory on classpath with clj migrations
      -t, --table NAME         migrations  Migration table name
      -h, --help

    Actions:
      create         Create a skeleton for a db migration
      migrate-up     Run all the up migrations
      migrate-down   Run all the down migrations

## License

Copyright © 2015 SoundCloud

Distributed under [The MIT License](http://opensource.org/licenses/mit-license.php).
