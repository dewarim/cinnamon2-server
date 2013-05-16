alter table relationtypes add column clone_on_left_version boolean not null default false;
alter table relationtypes add column clone_on_right_version boolean not null default false;
