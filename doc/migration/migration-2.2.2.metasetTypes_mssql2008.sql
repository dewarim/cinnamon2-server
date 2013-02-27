USE [demo]
GO
/****** Object:  Table [dbo].[metaset_types]    Script Date: 02/27/2013 14:00:39 ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[metaset_types](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[config] [ntext] NOT NULL,
	[description] [nvarchar](255) NOT NULL,
	[obj_version] [bigint] NOT NULL,
	[name] [nvarchar](255) NOT NULL,
 CONSTRAINT [PK_metaset_types] PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX  = OFF, STATISTICS_NORECOMPUTE  = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS  = ON, ALLOW_PAGE_LOCKS  = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
SET IDENTITY_INSERT [dbo].[metaset_types] ON
INSERT [dbo].[metaset_types] ([id], [config], [description], [obj_version], [name]) VALUES (2, N'<metaset />', N'search', 0, N'search')
INSERT [dbo].[metaset_types] ([id], [config], [description], [obj_version], [name]) VALUES (4, N'<metaset>
  <cart_contents/>
</metaset>', N'cart', 1, N'cart')
INSERT [dbo].[metaset_types] ([id], [config], [description], [obj_version], [name]) VALUES (5, N'<metaset />', N'translation_extension', 0, N'translation_extension')
INSERT [dbo].[metaset_types] ([id], [config], [description], [obj_version], [name]) VALUES (6, N'<metaset />', N'render_input', 0, N'render_input')
INSERT [dbo].[metaset_types] ([id], [config], [description], [obj_version], [name]) VALUES (7, N'<metaset />', N'render_output', 0, N'render_output')
INSERT [dbo].[metaset_types] ([id], [config], [description], [obj_version], [name]) VALUES (8, N'<metaset />', N'test', 0, N'test')
INSERT [dbo].[metaset_types] ([id], [config], [description], [obj_version], [name]) VALUES (9, N'<metaset />', N'tika', 0, N'tika')
INSERT [dbo].[metaset_types] ([id], [config], [description], [obj_version], [name]) VALUES (11, N'<metaset />', N'translation_folder', 0, N'translation_folder')
INSERT [dbo].[metaset_types] ([id], [config], [description], [obj_version], [name]) VALUES (12, N'<metaset />', N'translation_task', 0, N'translation_task')
INSERT [dbo].[metaset_types] ([id], [config], [description], [obj_version], [name]) VALUES (15, N'<metaset />', N'literature_set', 0, N'literature_set')
INSERT [dbo].[metaset_types] ([id], [config], [description], [obj_version], [name]) VALUES (17, N'<metaset />', N'excel', 0, N'excel')
INSERT [dbo].[metaset_types] ([id], [config], [description], [obj_version], [name]) VALUES (18, N'<metaset />', N'publication_log', 0, N'publication_log')
INSERT [dbo].[metaset_types] ([id], [config], [description], [obj_version], [name]) VALUES (19, N'<metaset />', N'task_definition', 0, N'task_definition')
INSERT [dbo].[metaset_types] ([id], [config], [description], [obj_version], [name]) VALUES (20, N'<metaset />', N'workflow_template', 0, N'workflow_template')
INSERT [dbo].[metaset_types] ([id], [config], [description], [obj_version], [name]) VALUES (21, N'<metaset />', N'transition', 0, N'transition')
SET IDENTITY_INSERT [dbo].[metaset_types] OFF
