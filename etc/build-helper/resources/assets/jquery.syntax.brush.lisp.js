// This file is part of the "jQuery.Syntax" project, and is distributed under the MIT License.
// Copyright (c) 2011 Samuel G. D. Williams. <http://www.oriontransfer.co.nz>


Syntax.lib.lispStyleComment={pattern:/(;+) .*$/gm,klass:'comment',allow:['href']};Syntax.register('lisp',function(brush){brush.push(['(',')'],{klass:'operator'});brush.push(Syntax.lib.lispStyleComment);brush.push(Syntax.lib.hexNumber);brush.push(Syntax.lib.decimalNumber);brush.push(Syntax.lib.webLink);brush.push({pattern:/\(\s*([^\s\(\)]+)/gmi,matches:Syntax.extractMatches({klass:'function'})});brush.push({pattern:/#[a-z]+/gi,klass:'constant'})
brush.push(Syntax.lib.multiLineDoubleQuotedString);brush.push(Syntax.lib.stringEscape);});