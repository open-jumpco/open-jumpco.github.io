<#include "header.ftl">

<#include "menu.ftl">

<div class="page-header">
    <h1>Projects</h1>
</div>

<#list projects as project>
    <#if (project.status == "published")>
        <div class="jumbotron">
            <h2><#escape x as x?xml>${project.title}</#escape></h2>
            <p>${project.summary}</p>
            <p><a class="btn btn-primary btn-lg" href="${project.uri}">Learn more</a></p>
        </div>
    </#if>
</#list>


<div class="page-header">
    <h1>Blog</h1>
</div>
<#list posts as post>
    <#if (post.status == "published")>
        <a href="${post.uri}"><h1><#escape x as x?xml>${post.title}</#escape></h1></a>
        <p>${post.date?string("dd MMMM yyyy")}</p>
        <p>${post.summary}</p>
    </#if>
</#list>

<hr/>

<p>Older posts are available in the <a href="${content.rootpath}${config.archive_file}">archive</a>.</p>

<#include "footer.ftl">
