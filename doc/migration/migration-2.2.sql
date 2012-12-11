-- Migration script to update Cinnamon repositories from 2.1.x to 2.2.0
-- usage on Linux: sudo -u postgres psql -f migration-2.2.sql repositoryName
--
-- PostgreSQL database dump
--

-- Dumped from database version 9.0.5
-- Dumped by pg_dump version 9.1.3
-- Started on 2012-03-19 10:33:57 CET

SET statement_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = off;
SET check_function_bodies = false;
SET client_min_messages = warning;
SET escape_string_warning = off;

SET search_path = public, pg_catalog;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- TOC entry 152 (class 1259 OID 166159)
-- Dependencies: 5
-- Name: folder_metasets; Type: TABLE; Schema: public; Owner: cinnamon; Tablespace: 
--

CREATE TABLE folder_metasets (
    id bigint NOT NULL,
    obj_version bigint,
    folder_id bigint NOT NULL,
    metaset_id bigint NOT NULL
);


ALTER TABLE public.folder_metasets OWNER TO cinnamon;

--
-- TOC entry 165 (class 1259 OID 166267)
-- Dependencies: 5
-- Name: metaset_types; Type: TABLE; Schema: public; Owner: cinnamon; Tablespace: 
--

CREATE TABLE metaset_types (
    id bigint NOT NULL,
    config character varying(255) NOT NULL,
    description character varying(255) NOT NULL,
    name character varying(128) NOT NULL,
    obj_version bigint
);


ALTER TABLE public.metaset_types OWNER TO cinnamon;

--
-- TOC entry 166 (class 1259 OID 166277)
-- Dependencies: 5
-- Name: metasets; Type: TABLE; Schema: public; Owner: cinnamon; Tablespace: 
--

CREATE TABLE metasets (
    id bigint NOT NULL,
    content character varying(10485760) NOT NULL,
    obj_version bigint,
    type_id bigint NOT NULL
);


ALTER TABLE public.metasets OWNER TO cinnamon;

--
-- TOC entry 169 (class 1259 OID 166303)
-- Dependencies: 5
-- Name: osd_metasets; Type: TABLE; Schema: public; Owner: cinnamon; Tablespace: 
--

CREATE TABLE osd_metasets (
    id bigint NOT NULL,
    obj_version bigint,
    metaset_id bigint NOT NULL,
    osd_id bigint NOT NULL
);


ALTER TABLE public.osd_metasets OWNER TO cinnamon;

--
-- TOC entry 1921 (class 0 OID 166159)
-- Dependencies: 152
-- Data for Name: folder_metasets; Type: TABLE DATA; Schema: public; Owner: cinnamon
--

COPY folder_metasets (id, obj_version, folder_id, metaset_id) FROM stdin;
\.


--
-- TOC entry 1922 (class 0 OID 166267)
-- Dependencies: 165
-- Data for Name: metaset_types; Type: TABLE DATA; Schema: public; Owner: cinnamon
--

COPY metaset_types (id, config, description, name, obj_version) FROM stdin;
15	<metaset />	search	search	0
16	<metaset />	cart	cart	0
17	<metaset />	translation_extension	translation_extension	0
18	<metaset />	render_input	render_input	0
19	<metaset />	render_output	render_output	0
20	<metaset />	test	test	0
21	<metaset />	tika	tika	0
22  <metaset /> translation_folder  translation_folder  0
22  <metaset /> translation_task  translation_task  0
\.


--
-- TOC entry 1923 (class 0 OID 166277)
-- Dependencies: 166
-- Data for Name: metasets; Type: TABLE DATA; Schema: public; Owner: cinnamon
--

COPY metasets (id, content, obj_version, type_id) FROM stdin;
\.


--
-- TOC entry 1924 (class 0 OID 166303)
-- Dependencies: 169
-- Data for Name: osd_metasets; Type: TABLE DATA; Schema: public; Owner: cinnamon
--

COPY osd_metasets (id, obj_version, metaset_id, osd_id) FROM stdin;
\.


--
-- TOC entry 1907 (class 2606 OID 166163)
-- Dependencies: 152 152
-- Name: folder_metasets_pkey; Type: CONSTRAINT; Schema: public; Owner: cinnamon; Tablespace: 
--

ALTER TABLE ONLY folder_metasets
    ADD CONSTRAINT folder_metasets_pkey PRIMARY KEY (id);


--
-- TOC entry 1909 (class 2606 OID 166276)
-- Dependencies: 165 165
-- Name: metaset_types_name_key; Type: CONSTRAINT; Schema: public; Owner: cinnamon; Tablespace: 
--

ALTER TABLE ONLY metaset_types
    ADD CONSTRAINT metaset_types_name_key UNIQUE (name);


--
-- TOC entry 1911 (class 2606 OID 166274)
-- Dependencies: 165 165
-- Name: metaset_types_pkey; Type: CONSTRAINT; Schema: public; Owner: cinnamon; Tablespace: 
--

ALTER TABLE ONLY metaset_types
    ADD CONSTRAINT metaset_types_pkey PRIMARY KEY (id);


--
-- TOC entry 1913 (class 2606 OID 166284)
-- Dependencies: 166 166
-- Name: metasets_pkey; Type: CONSTRAINT; Schema: public; Owner: cinnamon; Tablespace: 
--

ALTER TABLE ONLY metasets
    ADD CONSTRAINT metasets_pkey PRIMARY KEY (id);


--
-- TOC entry 1915 (class 2606 OID 166307)
-- Dependencies: 169 169
-- Name: osd_metasets_pkey; Type: CONSTRAINT; Schema: public; Owner: cinnamon; Tablespace: 
--

ALTER TABLE ONLY osd_metasets
    ADD CONSTRAINT osd_metasets_pkey PRIMARY KEY (id);


--
-- TOC entry 1919 (class 2606 OID 166547)
-- Dependencies: 169 167
-- Name: fk900cbe357da11b4b; Type: FK CONSTRAINT; Schema: public; Owner: cinnamon
--

ALTER TABLE ONLY osd_metasets
    ADD CONSTRAINT fk900cbe357da11b4b FOREIGN KEY (osd_id) REFERENCES objects(id);


--
-- TOC entry 1920 (class 2606 OID 166552)
-- Dependencies: 1912 169 166
-- Name: fk900cbe35b67890cf; Type: FK CONSTRAINT; Schema: public; Owner: cinnamon
--

ALTER TABLE ONLY osd_metasets
    ADD CONSTRAINT fk900cbe35b67890cf FOREIGN KEY (metaset_id) REFERENCES metasets(id);


--
-- TOC entry 1916 (class 2606 OID 166407)
-- Dependencies: 166 152 1912
-- Name: fkceca8c87b67890cf; Type: FK CONSTRAINT; Schema: public; Owner: cinnamon
--

ALTER TABLE ONLY folder_metasets
    ADD CONSTRAINT fkceca8c87b67890cf FOREIGN KEY (metaset_id) REFERENCES metasets(id);


--
-- TOC entry 1917 (class 2606 OID 166412)
-- Dependencies: 154 152
-- Name: fkceca8c87f57b3425; Type: FK CONSTRAINT; Schema: public; Owner: cinnamon
--

ALTER TABLE ONLY folder_metasets
    ADD CONSTRAINT fkceca8c87f57b3425 FOREIGN KEY (folder_id) REFERENCES folders(id);


--
-- TOC entry 1918 (class 2606 OID 166482)
-- Dependencies: 1910 166 165
-- Name: fke5345bd6abf96b0c; Type: FK CONSTRAINT; Schema: public; Owner: cinnamon
--

ALTER TABLE ONLY metasets
    ADD CONSTRAINT fke5345bd6abf96b0c FOREIGN KEY (type_id) REFERENCES metaset_types(id);


-- Completed on 2012-03-19 10:33:57 CET

--
-- PostgreSQL database dump complete
--

