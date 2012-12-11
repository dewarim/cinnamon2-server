if exists (select * from dbo.sysobjects where id = object_id(N'[dbo].[FK_metasets_metaset_types]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [dbo].[metasets] DROP CONSTRAINT FK_metasets_metaset_types
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[dbo].[FK_folder_metasets_metasets]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [dbo].[folder_metasets] DROP CONSTRAINT FK_folder_metasets_metasets
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[dbo].[FK_osd_metasets_metasets]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [dbo].[osd_metasets] DROP CONSTRAINT FK_osd_metasets_metasets
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[dbo].[folder_metasets]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
drop table [dbo].[folder_metasets]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[dbo].[lifecycle_log]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
drop table [dbo].[lifecycle_log]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[dbo].[metaset_types]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
drop table [dbo].[metaset_types]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[dbo].[metasets]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
drop table [dbo].[metasets]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[dbo].[osd_metasets]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
drop table [dbo].[osd_metasets]
GO

CREATE TABLE [dbo].[folder_metasets] (
	[id] [bigint] IDENTITY (1, 1) NOT NULL ,
	[obj_version] [bigint] NULL ,
	[folder_id] [bigint] NOT NULL ,
	[metaset_id] [bigint] NOT NULL 
) ON [PRIMARY]
GO

CREATE TABLE [dbo].[lifecycle_log] (
	[id] [bigint] IDENTITY (1, 1) NOT NULL ,
	[repository] [nvarchar] (255) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL ,
	[hibernate_id] [bigint] NOT NULL ,
	[user_name] [nvarchar] (255) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL ,
	[user_id] [bigint] NOT NULL ,
	[date_created] [timestamp] NOT NULL ,
	[lifecycle_id] [bigint] NOT NULL ,
	[lifecycle_name] [nvarchar] (255) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL ,
	[old_state_id] [bigint] NOT NULL ,
	[old_state_name] [nvarchar] (255) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL ,
	[new_state_id] [bigint] NOT NULL ,
	[new_state_name] [nvarchar] (255) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL ,
	[folder_path] [nvarchar] (4000) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL ,
	[name] [nvarchar] (255) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL 
) ON [PRIMARY]
GO

CREATE TABLE [dbo].[metaset_types] (
	[id] [bigint] IDENTITY (1, 1) NOT NULL ,
	[config] [ntext] COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL ,
	[description] [nvarchar] (255) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL ,
	[obj_version] [bigint] NOT NULL ,
	[name] [nvarchar] (255) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL 
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO

CREATE TABLE [dbo].[metasets] (
	[id] [bigint] IDENTITY (1, 1) NOT NULL ,
	[content] [ntext] COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL ,
	[obj_version] [bigint] NOT NULL ,
	[type_id] [bigint] NOT NULL 
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO

CREATE TABLE [dbo].[osd_metasets] (
	[id] [bigint] IDENTITY (1, 1) NOT NULL ,
	[obj_version] [bigint] NOT NULL ,
	[osd_id] [bigint] NOT NULL ,
	[metaset_id] [bigint] NOT NULL 
) ON [PRIMARY]
GO

