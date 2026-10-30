import fs from 'node:fs';import path from 'node:path';import {createRequire} from 'node:module';
const project=process.cwd(),require=createRequire(path.join(project,'package.json')),ts=require('typescript');
const root=path.join(project,'src/app');const walk=d=>fs.readdirSync(d,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(d,e.name)):[path.join(d,e.name)]);
const config=ts.readConfigFile(path.join(project,'tsconfig.json'),ts.sys.readFile).config;const parsed=ts.parseJsonConfigFileContent(config,ts.sys,project);const failures=[];let edges=0,files=0;
for(const file of walk(root).filter(p=>p.endsWith('.ts')&&!p.endsWith('.spec.ts'))){files++;const from=path.relative(root,file).replaceAll('\\','/'),feature=from.match(/^features\/([^/]+)\//)?.[1];const source=ts.createSourceFile(file,fs.readFileSync(file,'utf8'),ts.ScriptTarget.Latest,true);const fail=(n,message)=>failures.push(`${from}:${source.getLineAndCharacterOfPosition(n.getStart(source)).line+1}: ${message}`);
 function visit(n){let literal;if(ts.isImportDeclaration(n)||ts.isExportDeclaration(n))literal=n.moduleSpecifier;else if(ts.isCallExpression(n)&&n.expression.kind===ts.SyntaxKind.ImportKeyword)literal=n.arguments[0];if(literal&&ts.isStringLiteral(literal)){edges++;const spec=literal.text;const resolved=ts.resolveModuleName(spec,file,parsed.options,ts.sys).resolvedModule?.resolvedFileName;const target=resolved?path.relative(root,resolved).replaceAll('\\','/'):spec,other=target.match(/^features\/([^/]+)\//)?.[1];
 if(/^(core|shared)\//.test(from)&&/^(features|layout)\//.test(target))fail(n,'core/shared must not depend on features or layout');
 if(feature&&other&&feature!==other&&!/^features\/[^/]+\/public-api\.ts$/.test(target))fail(n,'consume other features through public-api.ts');
 if(from.includes('/domain/')&&(spec.startsWith('@angular/')||spec.startsWith('rxjs')||/\/(data-access|application|pages|ui)\//.test(target)))fail(n,'domain must remain independent of framework, adapters and presentation');
 if(from.includes('/application/')&&/\/(data-access|pages|ui)\//.test(target))fail(n,'application depends on ports, not concrete adapters or views');
 if(from.includes('/data-access/')&&/\/(pages|ui)\//.test(target))fail(n,'data-access must not import presentation');
 if(spec==='@angular/common/http'&&/\bHttpClient\b/.test(n.getText(source))&&!/^(core\/(http|session|configuration)|features\/[^/]+\/data-access)\//.test(from))fail(n,'HTTP belongs in a data-access adapter or core transport/session');
 }
 if(from.endsWith('.routes.ts')&&ts.isPropertyAssignment(n)&&n.name.getText(source)==='component')fail(n,'route entry screens must use loadComponent');
 ts.forEachChild(n,visit);
 }visit(source);
}
if(failures.length){console.error(failures.join('\n'));process.exitCode=1;}else console.log(JSON.stringify({passed:true,files,edges,policy:'core/features/layout, public APIs, independent domain, ports and deferred routes'}));
