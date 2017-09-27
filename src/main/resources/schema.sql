create table if not exists events (
  internal_id bigint auto_increment primary key,
  id bigint not null,
  name varchar(256) not null,
  time_of_start timestamp not null
);