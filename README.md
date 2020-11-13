

VMeeting-server
===

- :us: **Description**: This project is based on OpenVidu server 2.12. 
- :cn: **说明**: 基于 OpenVidu server 改造的服务端代码，简化结构. 
- :jp: **説明**: OpenVidu server　V２.１２ をベースに、プロジェクトの構造を簡略化する。   
  
  
  
  
- :beer: **已完成**:    
    删除docker方式（目前docker只用于混流录制，意义不大。计划以后用docker外的方法(FFmpeg)进行混流录制。）目前采用单个流录制的方式。  
    升高springboot版本 1.X -> 2.1.3  
    解决升级springboot版本后，[x-webkit-deflate-frame] is not supported问题（只在ios上面出现）  
    修改部分接口的认证
    修改ios13.6以上黑屏的问题(Can’t add existing ICE candidates to null WebRtcEndpoint)
    非主持人时、不允许创建会议(create session)
    
- :construction_worker_man: **Plan**:   
    变更验证机制
    修改coturn的认证生成机制
