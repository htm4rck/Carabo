import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import angular from 'angular-eslint';
export default tseslint.config(
 {ignores:['dist/**','node_modules/**','public/**','server/**','**/build-info.ts','test-results/**','playwright-report/**']},
 {files:['src/**/*.ts'],extends:[js.configs.recommended,...tseslint.configs.recommended,...angular.configs.tsRecommended],processor:angular.processInlineTemplates,rules:{'@typescript-eslint/no-explicit-any':'off','@typescript-eslint/no-unused-vars':['error',{argsIgnorePattern:'^_',varsIgnorePattern:'^_',caughtErrors:'none'}],'@angular-eslint/prefer-inject':'off','@angular-eslint/no-output-native':'off','no-console':['error',{allow:['error','warn']}]}},
 {files:['src/**/*.html'],extends:[...angular.configs.templateRecommended],rules:{'@angular-eslint/template/eqeqeq':'error'}}
);
