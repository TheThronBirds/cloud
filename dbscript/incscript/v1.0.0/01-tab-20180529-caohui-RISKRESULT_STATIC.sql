﻿-----------------------------------------------------------------------------------------------------------------------------------
----1. 增加表格RISKRESULT_STATIC，记录静态计算结果信息
DECLARE

I_EXISTS   NUMBER;
BEGIN
  SELECT COUNT(1) INTO I_EXISTS  FROM USER_TABLES A WHERE A.TABLE_NAME = 'RISKRESULT_STATIC';
  
  IF I_EXISTS = 0 THEN
    EXECUTE IMMEDIATE '
                          CREATE TABLE RISKRESULT_STATIC
                        (
                          VC_RESULT_ID              VARCHAR2(50),
                          I_RIKS_ID                NUMBER ,
                          VC_RISK_DESCRIPTION      VARCHAR2(300) ,
                          VC_OFFEND_TYPE           VARCHAR2(2000) ,
                          I_SPRING_DATE            NUMBER ,
                          I_SPRING_TIME            NUMBER ,
                          C_DECLARE_TYPE           VARCHAR2(50) ,
                          EN_VALVE_VALUE           NUMBER(32,8) ,
                          EN_RESULT_VALUE          NUMBER(32,8) ,
                          VC_INDEX_UNIT            VARCHAR2(2) ,
                          I_COMPARE_BS             VARCHAR2(8) ,
                          VC_DETAIL_INFOS          VARCHAR2(2000),
                          VC_USER                  VARCHAR2(20) ,
                          I_INSTRUCTION_ID         INTEGER ,
                          VC_STOCK_BOUND           VARCHAR2(1000) ,
                          VC_OBJ_CAPTION           VARCHAR2(100),
                          I_FUND                   INTEGER,
                          VC_CASH_BOUND            VARCHAR2(2000) ,
                          VC_STOCK_CODE            VARCHAR2(20) ,
                          VC_FZ_VALUE              VARCHAR2(50) ,
                          VC_FM_VALUE              VARCHAR2(50) ,
                          VC_KEY                   VARCHAR2(500) ,
                          VC_MULTI_INST_ID         VARCHAR2(50) ,
                          I_ENTRUST_ID             INTEGER ,
                          I_VIOLATION_DAYS         NUMBER(8) ,
                          VC_FZ_VALUE_DETAIL       VARCHAR2(2000) ,
                          VC_FM_VALUE_DETAIL       VARCHAR2(2000) ,
                          VC_STOCK_PROPERTY_TYPE   VARCHAR2(300) ,
                          VC_STOCK_PROPERTY_VALUE  VARCHAR2(300) ,
                          VC_INVEST_PROPERTY_TYPE  VARCHAR2(300) ,
                          VC_INVEST_PROPERTY_VALUE VARCHAR2(300) ,
                          I_FUND_ACCOUNT           NUMBER(22) ,
                          I_RIKS_ID_FROM           NUMBER,
                          C_REQUEST_TYPE           VARCHAR2(2),
                          VC_SERIAL_NUMBER          VARCHAR2(50)
                        )
                      
                      ';
  ELSE
     EXECUTE IMMEDIATE 'DROP TABLE RISKRESULT_STATIC';
     EXECUTE IMMEDIATE '
                          CREATE TABLE RISKRESULT_STATIC
                        (
                          VC_RESULT_ID              VARCHAR2(50),
                          I_RIKS_ID                NUMBER ,
                          VC_RISK_DESCRIPTION      VARCHAR2(300) ,
                          VC_OFFEND_TYPE           VARCHAR2(2000) ,
                          I_SPRING_DATE            NUMBER ,
                          I_SPRING_TIME            NUMBER ,
                          C_DECLARE_TYPE           VARCHAR2(50) ,
                          EN_VALVE_VALUE           NUMBER(32,8) ,
                          EN_RESULT_VALUE          NUMBER(32,8) ,
                          VC_INDEX_UNIT            VARCHAR2(2) ,
                          I_COMPARE_BS             VARCHAR2(8) ,
                          VC_DETAIL_INFOS          VARCHAR2(2000),
                          VC_USER                  VARCHAR2(20) ,
                          I_INSTRUCTION_ID         INTEGER ,
                          VC_STOCK_BOUND           VARCHAR2(1000) ,
                          VC_OBJ_CAPTION           VARCHAR2(100),
                          I_FUND                   INTEGER,
                          VC_CASH_BOUND            VARCHAR2(2000) ,
                          VC_STOCK_CODE            VARCHAR2(20) ,
                          VC_FZ_VALUE              VARCHAR2(50) ,
                          VC_FM_VALUE              VARCHAR2(50) ,
                          VC_KEY                   VARCHAR2(500) ,
                          VC_MULTI_INST_ID         VARCHAR2(50) ,
                          I_ENTRUST_ID             INTEGER ,
                          I_VIOLATION_DAYS         NUMBER(8) ,
                          VC_FZ_VALUE_DETAIL       VARCHAR2(2000) ,
                          VC_FM_VALUE_DETAIL       VARCHAR2(2000) ,
                          VC_STOCK_PROPERTY_TYPE   VARCHAR2(300) ,
                          VC_STOCK_PROPERTY_VALUE  VARCHAR2(300) ,
                          VC_INVEST_PROPERTY_TYPE  VARCHAR2(300) ,
                          VC_INVEST_PROPERTY_VALUE VARCHAR2(300) ,
                          I_FUND_ACCOUNT           NUMBER(22) ,
                          I_RIKS_ID_FROM           NUMBER,
                          C_REQUEST_TYPE           VARCHAR2(2),
                          VC_SERIAL_NUMBER          VARCHAR2(50)
                        )
                      
                      ';
  END IF;


END;
---------------------------------------------------------------------------------------------------------------------------------