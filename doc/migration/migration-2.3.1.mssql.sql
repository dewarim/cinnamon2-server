USE [demo]
GO

/****** Object:  Table [dbo].[links]    Script Date: 01/08/2013 06:40:33 ******/
SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[links](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[type] [nvarchar](255) NOT NULL,
	[resolver] [nvarchar](255) NOT NULL,
	[owner_id] [bigint] NOT NULL,
	[parent_id] [bigint] NOT NULL,
	[acl_id] [bigint] NOT NULL,
	[folder_id] [bigint] NULL,
	[osd_id] [bigint] NULL,
  [version] [bigint] default 0,
 CONSTRAINT [PK_links] PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX  = OFF, STATISTICS_NORECOMPUTE  = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS  = ON, ALLOW_PAGE_LOCKS  = ON) ON [PRIMARY]
) ON [PRIMARY]

GO

ALTER TABLE [dbo].[links]  WITH CHECK ADD  CONSTRAINT [FK_links_acls] FOREIGN KEY([acl_id])
REFERENCES [dbo].[acls] ([id])
GO

ALTER TABLE [dbo].[links] CHECK CONSTRAINT [FK_links_acls]
GO

ALTER TABLE [dbo].[links]  WITH CHECK ADD  CONSTRAINT [FK_links_folder_parent] FOREIGN KEY([parent_id])
REFERENCES [dbo].[folders] ([id])
GO

ALTER TABLE [dbo].[links] CHECK CONSTRAINT [FK_links_folder_parent]
GO

ALTER TABLE [dbo].[links]  WITH CHECK ADD  CONSTRAINT [FK_links_folders] FOREIGN KEY([folder_id])
REFERENCES [dbo].[folders] ([id])
GO

ALTER TABLE [dbo].[links] CHECK CONSTRAINT [FK_links_folders]
GO

ALTER TABLE [dbo].[links]  WITH CHECK ADD  CONSTRAINT [FK_links_objects] FOREIGN KEY([osd_id])
REFERENCES [dbo].[objects] ([id])
GO

ALTER TABLE [dbo].[links] CHECK CONSTRAINT [FK_links_objects]
GO

ALTER TABLE [dbo].[links]  WITH CHECK ADD  CONSTRAINT [FK_links_users] FOREIGN KEY([owner_id])
REFERENCES [dbo].[users] ([id])
GO

ALTER TABLE [dbo].[links] CHECK CONSTRAINT [FK_links_users]
GO

