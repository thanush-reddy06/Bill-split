import {useEffect, useState} from 'react';
import {api} from './api';

const money = (c, currency = 'INR') => new Intl.NumberFormat('en-IN', {style:'currency', currency}).format(c / 100);
const blank = () => ({title:'', currency:'INR', total:'', mode:'SETTLE', version:null, participants:[{name:'',paid:''},{name:'',paid:''}]});
const go = page => { window.location.hash = '/' + page; };

export default function App() {
 const [user,setUser] = useState(null), [loading,setLoading] = useState(true), [error,setError] = useState('');
 const [route,setRoute] = useState(window.location.hash.slice(2) || 'login');
 useEffect(() => {
  const change = () => setRoute(window.location.hash.slice(2) || 'login');
  const expired = () => {setUser(null);setError('Your session ended. Please log in again.');go('login');};
  window.addEventListener('hashchange',change);window.addEventListener('session-expired',expired);
  api('/auth/me').then(profile => {setUser(profile);go('app');}).catch(e => {if(e.status !== 401)setError(e.message);go('login');}).finally(() => setLoading(false));
  return () => {window.removeEventListener('hashchange',change);window.removeEventListener('session-expired',expired);};
 },[]);
 async function logout() {
  try {await api('/auth/logout',{method:'POST'});setUser(null);setError('');go('login');}
  catch(e) {setError(e.message);}
 }
 if(loading)return <main className="auth"><p>Loading…</p></main>;
 return <>
  <header><div className="header-inner"><strong>Bill Split <span>with Memory</span></strong>{user && <div className="actions"><span>Hi, {user.name}</span><button onClick={logout}>Log out</button></div>}</div></header>
  {error && <div className="global-error" role="alert">{error}<button onClick={()=>setError('')} aria-label="Dismiss message">×</button></div>}
  {user ? <Dashboard key={user.id}/> : <Auth register={route === 'register'} onLogin={profile=>{setUser(profile);setError('');go('app');}}/>}
  <footer>Bill Split with Memory · Share expenses, keep a record.</footer>
 </>;
}

function Auth({register,onLogin}) {
 const [name,setName]=useState(''),[email,setEmail]=useState(''),[password,setPassword]=useState(''),[confirm,setConfirm]=useState('');
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 useEffect(()=>{setError('');setPassword('');setConfirm('');},[register]);
 async function submit(e) {
  e.preventDefault();setError('');setNotice('');
  if(register && password!==confirm){setError('Passwords do not match.');return;}
  setBusy(true);
  try {
   if(register){await api('/auth/register',{method:'POST',body:JSON.stringify({name,email,password})});setNotice('Account created! Log in with your email and password.');go('login');}
   else {
    await api('/auth/login',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({email,password}).toString()});
    onLogin(await api('/auth/me'));
   }
  }catch(e){setError(e.message);}finally{setBusy(false);}
 }
 return <main className="auth"><section className="card">
  <h1>{register?'Create an account':'Welcome back'}</h1><p>{register?'Save your bills in your own account.':'Log in to calculate and view your saved bills.'}</p>
  {notice&&<p className="notice" role="status">{notice}</p>}{error&&<p className="error" role="alert">{error}</p>}
  <form onSubmit={submit}><fieldset disabled={busy}>
   {register&&<label>Your name<input required maxLength={60} autoComplete="name" value={name} onChange={e=>setName(e.target.value)}/></label>}
   <label>Email<input required type="email" maxLength={254} autoComplete="username" value={email} onChange={e=>setEmail(e.target.value)}/></label>
   <label>Password<input required type="password" minLength={register?8:undefined} autoComplete={register?'new-password':'current-password'} value={password} onChange={e=>setPassword(e.target.value)}/></label>
   {register&&<><small>Use at least 8 characters.</small><label>Confirm password<input required type="password" autoComplete="new-password" value={confirm} onChange={e=>setConfirm(e.target.value)}/></label></>}
   <button className="primary full" type="submit">{busy?'Please wait…':register?'Register':'Log in'}</button>
  </fieldset></form>
  <p className="center">{register?'Already have an account?':'New here?'} <a href={register?'#/login':'#/register'}>{register?'Log in':'Register'}</a></p>
 </section></main>;
}

function Dashboard() {
 const [page,setPage]=useState('new'),[form,setForm]=useState(blank),[trips,setTrips]=useState([]),[preview,setPreview]=useState(null);
 const [active,setActive]=useState(null),[editing,setEditing]=useState(null),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const [historyError,setHistoryError]=useState(''),[loading,setLoading]=useState(true),[search,setSearch]=useState(''),[filter,setFilter]=useState('all');
 async function loadHistory(){setLoading(true);try{setTrips(await api('/trips'));setHistoryError('');}catch(e){setHistoryError(e.message);}finally{setLoading(false);}}
 useEffect(()=>{loadHistory();},[]);
 function navigate(next){setPage(next);setError('');setNotice('');}
 function newSplit(){setForm(blank());setPreview(null);setEditing(null);setActive(null);navigate('new');}
 function change(key,value){setForm(f=>({...f,[key]:value}));setPreview(null);setError('');}
 function person(index,key,value){change('participants',form.participants.map((p,i)=>i===index?{...p,[key]:value}:p));}
 function resize(value){const n=Number(value);if(n<2||n>50)return;
  if(n<form.participants.length&&form.participants.slice(n).some(p=>p.name||p.paid)&&!window.confirm('Remove the last people and their entries?'))return;
  change('participants',Array.from({length:n},(_,i)=>form.participants[i]||{name:'',paid:''}));
 }
 function payload(){return {...form,participants:form.participants.map(p=>({name:p.name.trim(),paid:form.mode==='EQUAL'?'0':p.paid||'0'}))};}
 async function calculate(e){e.preventDefault();setBusy(true);setError('');try{setPreview(await api('/calculate',{method:'POST',body:JSON.stringify(payload())}));}catch(e){setError(e.message);}finally{setBusy(false);}}
 async function save(){setBusy(true);setError('');try{setActive(await api(editing?'/trips/'+editing:'/trips',{method:editing?'PUT':'POST',body:JSON.stringify(payload())}));setPreview(null);setEditing(null);navigate('detail');setNotice('Saved to your history.');await loadHistory();}catch(e){setError(e.message);}finally{setBusy(false);}}
 async function openTrip(id){setBusy(true);setError('');try{setActive(await api('/trips/'+id));navigate('detail');}catch(e){setError(e.message);}finally{setBusy(false);}}
 function edit(){if(active.settled&&!window.confirm('Saving changes will reopen this split. Continue?'))return;
  setForm({title:active.title,currency:active.currency,total:(active.calculation.totalCents/100).toFixed(2),mode:active.calculation.mode,version:active.version,participants:active.calculation.participants.map(p=>({name:p.name,paid:(p.paidCents/100).toFixed(2)}))});setEditing(active.id);setPreview(null);navigate('new');
 }
 async function status(){setBusy(true);setError('');try{setActive(await api('/trips/'+active.id+'/status',{method:'PATCH',body:JSON.stringify({settled:!active.settled,version:active.version})}));await loadHistory();}catch(e){setError(e.message);}finally{setBusy(false);}}
 async function remove(){if(!window.confirm('Delete this saved split?'))return;setBusy(true);try{await api('/trips/'+active.id,{method:'DELETE'});setActive(null);navigate('history');await loadHistory();}catch(e){setError(e.message);}finally{setBusy(false);}}
 function demo(){setForm({title:'Dinner with friends',currency:'INR',total:'3000',mode:'SETTLE',version:null,participants:[{name:'Thanush',paid:'2000'},{name:'Arjun',paid:'1000'},{name:'Sai',paid:'0'}]});setEditing(null);setPreview(null);setError('');}
 function download(){const t=active,c=t.calculation;const lines=[t.title,'Total: '+money(c.totalCents,t.currency),'Status: '+(t.settled?'Settled':'Open'),'',...c.participants.map(p=>`${p.name}: paid ${money(p.paidCents,t.currency)} | share ${money(p.shareCents,t.currency)} | ${balanceLabel(p,t.currency)}`),'',...(c.mode==='EQUAL'?c.participants.map(p=>`${p.name} pays ${money(p.shareCents,t.currency)} toward the bill`):c.transfers.map(t=>`${t.from} pays ${t.to} ${money(t.amountCents,active.currency)}`)),'','Original calculation. The app does not transfer money.'];const url=URL.createObjectURL(new Blob([lines.join('\n')],{type:'text/plain;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download='bill-split-'+t.id+'.txt';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
 const shown=trips.filter(t=>t.title.toLowerCase().includes(search.toLowerCase())&&(filter==='all'||(filter==='settled'?t.settled:!t.settled)));
 const paid=form.participants.reduce((s,p)=>s+Math.round(Number(p.paid||0)*100),0);
 return <main className="workspace">
  <nav aria-label="Main navigation"><button className={page==='new'?'selected':''} disabled={busy} onClick={newSplit}>New split</button><button className={page==='history'?'selected':''} disabled={busy} onClick={()=>navigate('history')}>My history ({trips.length})</button></nav>
  <h1>{page==='history'?'My saved bills':page==='detail'?active?.title:editing?'Edit split':'Split a bill'}</h1>
  {error&&<p className="error" role="alert">{error}</p>}{notice&&<p className="notice" role="status">{notice}</p>}
  {historyError&&<p className="error" role="alert">{historyError} <button onClick={loadHistory} disabled={loading}>Retry</button></p>}
  {page==='new'&&<><p>Enter the bill and everyone who shared it. Include people who paid nothing.</p><div className="columns">
   <form className="card" onSubmit={calculate}><fieldset disabled={busy}>
    <div className="actions between"><h2>Bill details</h2>{!editing&&<button type="button" onClick={demo}>Try example</button>}</div>
    <label>Trip or dining name<input required maxLength={80} placeholder="e.g. Sunday lunch" value={form.title} onChange={e=>change('title',e.target.value)}/></label>
    <div className="fields"><label>Total expense<input required type="number" min="0.01" max="1000000000" step="0.01" value={form.total} onChange={e=>change('total',e.target.value)}/></label><label>Currency<select value={form.currency} onChange={e=>change('currency',e.target.value)}>{['INR','USD','EUR','GBP'].map(c=><option key={c}>{c}</option>)}</select></label></div>
    <label>Split type<select value={form.mode} onChange={e=>change('mode',e.target.value)}><option value="SETTLE">Settle payments — someone already paid</option><option value="EQUAL">Split equally — bill is not paid yet</option></select></label>
    <p className="help">{form.mode==='EQUAL'?'Each person pays their share toward the bill.':'Enter what each person already paid. Leave 0 for someone who paid nothing. Payments must add up to the total.'}</p>
    <label>Number of people<select value={form.participants.length} onChange={e=>resize(e.target.value)}>{Array.from({length:49},(_,i)=>i+2).map(n=><option key={n}>{n}</option>)}</select></label>
    {form.participants.map((p,i)=><div className="person" key={i}><label>Person {i+1}<input required maxLength={40} aria-label={'Person '+(i+1)+' name'} placeholder="Name" value={p.name} onChange={e=>person(i,'name',e.target.value)}/></label>{form.mode==='SETTLE'&&<label>Already paid<input type="number" min="0" max="1000000000" step="0.01" placeholder="0" aria-label={'Amount paid by person '+(i+1)} value={p.paid} onChange={e=>person(i,'paid',e.target.value)}/></label>}<button type="button" className="remove" disabled={form.participants.length<=2} aria-label={'Remove person '+(i+1)} onClick={()=>change('participants',form.participants.filter((_,j)=>j!==i))}>×</button></div>)}
    <button type="button" disabled={form.participants.length>=50} onClick={()=>resize(form.participants.length+1)}>+ Add person</button>
    {form.mode==='SETTLE'&&<p className="help">Total entered as paid: <strong>{money(paid,form.currency)}</strong></p>}
    <button className="primary full" type="submit">{busy?'Working…':'Calculate split'}</button>
   </fieldset></form>
   <div>{preview?<Result calculation={preview} currency={form.currency} save={save} busy={busy} editing={editing}/>:<section className="card"><h2>Your result</h2><p>Calculate the split to see each person’s share and how much they still owe.</p><p className="example">Example: ₹3,000 ÷ 3 people = ₹1,000 each. If Sai paid ₹0 and Thanush paid ₹2,000, Sai pays Thanush ₹1,000.</p></section>}</div>
  </div></>}
  {page==='history'&&<><div className="fields"><label>Search bills<input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Trip or dining name"/></label><label>Status<select value={filter} onChange={e=>setFilter(e.target.value)}><option value="all">All</option><option value="open">To settle</option><option value="settled">Settled</option></select></label></div>
   {loading?<p>Loading history…</p>:shown.length===0?<section className="card"><p>{trips.length?'No matching bills.':'No saved bills yet. Calculate a split and save it here.'}</p></section>:<div className="history-list">{shown.map(t=><button className="card history-item" key={t.id} disabled={busy} onClick={()=>openTrip(t.id)}><strong>{t.title}</strong><span>{money(t.calculation.totalCents,t.currency)} · {t.calculation.participants.length} people</span><small>{new Date(t.createdAt).toLocaleDateString()} · {t.settled?'Settled':'To settle'}</small></button>)}</div>}
  </>}
  {page==='detail'&&active&&<><div className="actions detail-actions"><button disabled={busy} onClick={()=>navigate('history')}>Back to history</button><button disabled={busy} onClick={edit}>Edit</button><button onClick={download}>Export</button><button className="danger" disabled={busy} onClick={remove}>Delete split</button></div><Result calculation={active.calculation} currency={active.currency} settled={active.settled}/><button className="primary" disabled={busy} onClick={status}>{active.settled?'Reopen split':'Mark all as settled'}</button><p className="help">This records the status. It does not send money.</p></>}
 </main>;
}
const balanceLabel=(p,currency)=>p.balanceCents===0?'Nothing to pay':(p.balanceCents>0?'Receives ':'Pays ')+money(Math.abs(p.balanceCents),currency);
function Result({calculation:c,currency,save,busy,editing,settled}) {
 const equal=c.mode==='EQUAL';
 return <section className="card result"><h2>{settled?'Settled':equal?'Each person’s share':'Who pays whom'}</h2>
  <p><strong>{money(c.totalCents,currency)}</strong> shared by {c.participants.length} people.</p>
  <div className="table-wrap"><table><thead><tr><th>Person</th><th>Paid</th><th>Share</th><th>{settled?'Original balance':'To settle'}</th></tr></thead><tbody>{c.participants.map(p=><tr key={p.name}><td>{p.name}</td><td>{money(p.paidCents,currency)}</td><td>{money(p.shareCents,currency)}</td><td>{balanceLabel(p,currency)}</td></tr>)}</tbody></table></div>
  {equal?<><p>{settled?'Original amounts due toward the bill:':'The bill has not been paid yet. Pay these amounts toward the bill:'}</p><ul className="payments">{c.participants.map(p=><li key={p.name}><strong>{p.name}</strong> pays <strong>{money(p.shareCents,currency)}</strong> toward the bill.</li>)}</ul></>:c.transfers.length?<ul className="payments">{c.transfers.map((t,i)=><li key={i}><strong>{t.from}</strong> pays <strong>{t.to}</strong> {money(t.amountCents,currency)}</li>)}</ul>:<p className="notice">Everyone has already paid their share. No repayments needed.</p>}
  <p className="help">{settled?'Marked as completed. These are the original amounts for your records.':'Previous payments are included in this calculation.'} Any extra paise or cents are assigned in the order people are listed.</p>
  {save&&<><button className="primary full" disabled={busy} onClick={save}>{busy?'Saving…':editing?'Save changes':'Save split to history'}</button><small>Save to keep this bill in your account.</small></>}
 </section>;
}
