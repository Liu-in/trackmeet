; ============================================================================
; TrackMeet 田径运动会编排软件 —— Inno Setup 安装脚本（Windows EXE 安装包）
;
; 编译方式（两种任选）：
;   1) 一键构建（推荐）：pwsh installer/build-installer.ps1
;   2) 手工：先用 installer/build-installer.ps1 -StageOnly 生成 installer\stage\，
;      再用 Inno Setup 编译本文件：ISCC.exe installer\TrackMeet.iss
;
; 支持静默安装：
;   TrackMeet-Setup-x.y.z.exe /VERYSILENT /SUPPRESSMSGBOXES /NORESTART ^
;       /DBHOST=127.0.0.1 /DBPORT=3306 /DBUSER=root /DBPASSWORD=xxx ^
;       /WEBPORT=8080 /ADMINPWD=Admin@123 /INITDB=1 /INSTALLSERVICE=1
; ============================================================================

#define MyAppName "TrackMeet 田径运动会编排软件"
#define MyAppShortName "TrackMeet"
#define MyAppVersion GetEnv("TRACKMEET_VERSION")
#if MyAppVersion == ""
  #define MyAppVersion "P6"
#endif
#define MyAppPublisher "TrackMeet"
#define MyAppURL "http://localhost/"
#define StageDir "stage"

[Setup]
AppId={{8F3B1C42-7D5A-4E19-9C2B-5A7E6D41C9A1}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppShortName}
DefaultGroupName={#MyAppShortName}
DisableProgramGroupPage=yes
OutputDir=..\dist
OutputBaseFilename=TrackMeet-Setup-{#MyAppVersion}
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayName={#MyAppName}
UninstallDisplayIcon={app}\assets\TrackMeet.ico
SetupLogging=yes
; 安装包图标（若 stage 中存在则使用）
#if FileExists(AddBackslash(SourcePath) + StageDir + "\assets\TrackMeet.ico")
SetupIconFile={#StageDir}\assets\TrackMeet.ico
#endif

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
; 简体中文语言文件并非 Inno Setup 官方自带（需自行放入 Languages 目录），存在时自动启用，否则回退英文界面
#if FileExists(AddBackslash(CompilerPath) + "Languages\ChineseSimplified.isl")
Name: "chinesesimplified"; MessagesFile: "compiler:Languages\ChineseSimplified.isl"
#endif

[Tasks]
Name: "desktopicon"; Description: "创建桌面快捷方式"; GroupDescription: "附加任务:"
Name: "autostart"; Description: "开机自动启动服务（推荐）"; GroupDescription: "附加任务:"
Name: "firewall"; Description: "为 Web 端口添加防火墙入站规则"; GroupDescription: "附加任务:"

[Files]
Source: "{#StageDir}\backend\*";        DestDir: "{app}\backend";   Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#StageDir}\frontend\*";       DestDir: "{app}\frontend";  Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#StageDir}\runtime\*";        DestDir: "{app}\runtime";   Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#StageDir}\sql\*";            DestDir: "{app}\sql";       Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#StageDir}\scripts\*";        DestDir: "{app}\scripts";   Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#StageDir}\assets\*";         DestDir: "{app}\assets";    Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#StageDir}\docs\*";           DestDir: "{app}\docs";      Flags: ignoreversion recursesubdirs createallsubdirs skipifsourcedoesntexist

[Dirs]
Name: "{app}\config"
Name: "{app}\logs"
Name: "{app}\backup"

[Icons]
Name: "{group}\启动 {#MyAppShortName}";       Filename: "{app}\scripts\start-backend.cmd"; IconFilename: "{app}\assets\TrackMeet.ico"; IconIndex: 0
Name: "{group}\打开管理端";                    Filename: "{sys}\cmd.exe"; Parameters: "/c start """" http://localhost:{code:GetWebPort}/"; IconFilename: "{app}\assets\TrackMeet.ico"
Name: "{group}\停止 {#MyAppShortName}";       Filename: "{app}\scripts\stop-backend.cmd"; IconFilename: "{app}\assets\TrackMeet.ico"
Name: "{group}\查看日志";                      Filename: "{app}\logs"
Name: "{group}\卸载 {#MyAppShortName}";       Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppShortName}";      Filename: "{app}\scripts\start-backend.cmd"; Tasks: desktopicon; IconFilename: "{app}\assets\TrackMeet.ico"

[Run]
; 1) 写入配置 + 初始化数据库 + 注册开机自启 + 启动服务
Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; \
  Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\scripts\configure.ps1"" -AppDir ""{app}"" -DbHost ""{code:GetDbHost}"" -DbPort ""{code:GetDbPort}"" -DbUser ""{code:GetDbUser}"" -DbPassword ""{code:GetDbPassword}"" -WebPort ""{code:GetWebPort}"" -AdminPassword ""{code:GetAdminPassword}"" -InitDb ""{code:GetInitDb}"" -AutoStart ""{code:GetAutoStart}"" -Firewall ""{code:GetFirewall}"""; \
  StatusMsg: "正在配置数据库并启动服务（首次安装需 1~3 分钟）..."; Flags: waituntilterminated runhidden
; 2) 打开管理端
Filename: "{sys}\cmd.exe"; Parameters: "/c start """" http://localhost:{code:GetWebPort}/"; \
  Description: "打开管理端"; Flags: postinstall nowait skipifsilent

[UninstallRun]
Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; \
  Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\scripts\uninstall.ps1"" -AppDir ""{app}"""; \
  Flags: waituntilterminated runhidden

[Code]
var
  DbPage: TInputQueryWizardPage;
  AppPage: TInputQueryWizardPage;

function GetParamOr(const Name, Default: String): String;
begin
  Result := ExpandConstant('{param:' + Name + '|' + Default + '}');
end;

procedure InitializeWizard;
begin
  { 数据库连接页 }
  DbPage := CreateInputQueryPage(wpSelectTasks,
    '数据库配置', '请填写 MySQL 8 的连接信息',
    'TrackMeet 需要一个 MySQL 8 数据库。安装向导将自动创建库、建表并导入初始数据（可重复执行）。');
  DbPage.Add('MySQL 主机：', False);
  DbPage.Add('MySQL 端口：', False);
  DbPage.Add('数据库账号：', False);
  DbPage.Add('数据库口令：', True);
  DbPage.Values[0] := GetParamOr('DBHOST', '127.0.0.1');
  DbPage.Values[1] := GetParamOr('DBPORT', '3306');
  DbPage.Values[2] := GetParamOr('DBUSER', 'root');
  DbPage.Values[3] := GetParamOr('DBPASSWORD', '');

  { 应用配置页 }
  AppPage := CreateInputQueryPage(DbPage.ID,
    '应用配置', '请设置 Web 端口与管理口令',
    'Web 端口用于浏览器访问管理端与大屏；管理口令用于内置管理员账号 admin。');
  AppPage.Add('Web 端口：', False);
  AppPage.Add('管理员初始口令（不少于 6 位）：', True);
  AppPage.Values[0] := GetParamOr('WEBPORT', '8080');
  AppPage.Values[1] := GetParamOr('ADMINPWD', 'Admin@123');
end;

function GetDbHost(Param: String): String;     begin Result := DbPage.Values[0]; end;
function GetDbPort(Param: String): String;     begin Result := DbPage.Values[1]; end;
function GetDbUser(Param: String): String;     begin Result := DbPage.Values[2]; end;
function GetDbPassword(Param: String): String; begin Result := DbPage.Values[3]; end;
function GetWebPort(Param: String): String;    begin Result := AppPage.Values[0]; end;
function GetAdminPassword(Param: String): String; begin Result := AppPage.Values[1]; end;
function GetInitDb(Param: String): String;     begin Result := GetParamOr('INITDB', '1'); end;
function GetAutoStart(Param: String): String;
begin
  if WizardIsTaskSelected('autostart') then Result := '1'
  else Result := GetParamOr('AUTOSTART', '0');
end;
function GetFirewall(Param: String): String;
begin
  if WizardIsTaskSelected('firewall') then Result := '1'
  else Result := GetParamOr('FIREWALL', '0');
end;

function NextButtonClick(CurPageID: Integer): Boolean;
var
  Port: Integer;
begin
  Result := True;
  if (CurPageID = DbPage.ID) then
  begin
    if DbPage.Values[0] = '' then begin MsgBox('请填写 MySQL 主机地址。', mbError, MB_OK); Result := False; end;
    if not TryStrToInt(DbPage.Values[1], Port) then begin MsgBox('MySQL 端口必须是数字。', mbError, MB_OK); Result := False; end;
    if DbPage.Values[2] = '' then begin MsgBox('请填写数据库账号。', mbError, MB_OK); Result := False; end;
  end;
  if (CurPageID = AppPage.ID) then
  begin
    if not TryStrToInt(AppPage.Values[0], Port) then begin MsgBox('Web 端口必须是数字。', mbError, MB_OK); Result := False; end;
    if Length(AppPage.Values[1]) < 6 then begin MsgBox('管理员口令至少 6 位。', mbError, MB_OK); Result := False; end;
  end;
end;
