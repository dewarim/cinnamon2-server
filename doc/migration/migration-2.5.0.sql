-- Table: index_jobs

-- DROP TABLE index_jobs;

CREATE TABLE index_jobs
(
  id bigserial NOT NULL,
  failed boolean NOT NULL,
  indexable_class character varying(255) NOT NULL,
  indexable_id bigint NOT NULL,
  CONSTRAINT index_jobs_pkey PRIMARY KEY (id )
)
WITH (
  OIDS=FALSE
);
ALTER TABLE index_jobs
  OWNER TO cinnamon;

--- Note: if your project uses a custom persistence.xml, you have to add the server.index.IndexJob class to the mapping.
