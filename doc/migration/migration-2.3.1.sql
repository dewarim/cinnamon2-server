-- migration script to upgrade a Cinnamon 2 repository from 2.2.2 (or later) to 2.3.1
-- database type: Postgresql 9.1

-- Table: links

-- DROP TABLE links;

CREATE TABLE links
(
  id bigint NOT NULL,
  type character varying(127) NOT NULL,
  resolver character varying(127) NOT NULL,
  owner_id bigint NOT NULL,
  acl_id bigint NOT NULL,
  parent_id bigint NOT NULL,
  folder_id bigint,
  osd_id bigint,
  version bigint,
  CONSTRAINT links_pk PRIMARY KEY (id ),
  CONSTRAINT links_folder_id_fk FOREIGN KEY (folder_id)
      REFERENCES folders (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT links_osd_id_fk FOREIGN KEY (osd_id)
      REFERENCES objects (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT links_owner_id_fk FOREIGN KEY (owner_id)
      REFERENCES users (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT links_parent_id_fk FOREIGN KEY (parent_id)
      REFERENCES folders (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION
)
WITH (
  OIDS=FALSE
);
ALTER TABLE links
  OWNER TO cinnamon;

-- Index: fki_links_folder_id_fk

-- DROP INDEX fki_links_folder_id_fk;

CREATE INDEX fki_links_folder_id_fk
  ON links
  USING btree
  (folder_id );

-- Index: fki_links_osd_id_fk

-- DROP INDEX fki_links_osd_id_fk;

CREATE INDEX fki_links_osd_id_fk
  ON links
  USING btree
  (osd_id );

-- Index: fki_links_parent_id_fk

-- DROP INDEX fki_links_parent_id_fk;

CREATE INDEX fki_links_parent_id_fk
  ON links
  USING btree
  (parent_id );

