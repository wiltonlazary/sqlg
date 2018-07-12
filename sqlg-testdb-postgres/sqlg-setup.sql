ALTER SYSTEM SET max_connections TO '1000';
ALTER SYSTEM SET max_locks_per_transaction TO '256';
ALTER SYSTEM SET shared_buffers TO '1024MB';

CREATE DATABASE "sqlgraphdb";
CREATE DATABASE "g1";
CREATE DATABASE "g2";
CREATE DATABASE "prototype";
CREATE DATABASE "readGraph";
CREATE DATABASE "standard";
CREATE DATABASE "subgraph";
CREATE DATABASE "temp";
CREATE DATABASE "temp1";
CREATE DATABASE "temp2";
CREATE DATABASE "target";
