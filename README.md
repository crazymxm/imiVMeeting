

VMeeting-server
===

- **Description**: This project is based on OpenVidu server 2.12. 
- **说明**: 基于 OpenVidu server 改造的服务端代码，简化结构. 
- **説明**: OpenVidu server　V２.１２ をベースに、プロジェクトの構造を簡略化する。   


- **已完成**:    
    删除docker方式。计划以后用docker外的方法(FFmpeg)进行混流录制。  
    升高springboot版本 1.X -> 2.1.3  
    解决升级springboot版本后，[x-webkit-deflate-frame] is not supported问题（只在ios上面出现）  

- **Plan**:   
    变更验证机制
    修改coturn的认证生成机制
